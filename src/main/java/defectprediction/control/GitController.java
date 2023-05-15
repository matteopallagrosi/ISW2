package defectprediction.control;

import defectprediction.Utils;
import defectprediction.model.Class;
import defectprediction.model.Version;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

public class GitController {

    private Repository repo;
    private Git git;
    private List<Version> releases;  //le releases del repository ordinate per releaseDate crescente

    public GitController(Repository repo, List<Version> versions) {
        this.repo = repo;
        git = new Git(repo);
        this.releases = versions;
    }

    //recupera tutti i commit di tutti i branch della repository corrente
    public List<RevCommit> getAllCommits() throws IOException {

        Collection<Ref> allRefs = repo.getRefDatabase().getRefs();
        List<RevCommit> allCommits = new ArrayList<>();

        // a RevWalk allows to walk over commits based on some filtering that is defined
        try (RevWalk revWalk = new RevWalk(repo)) {
            for( Ref ref : allRefs ) {
                revWalk.markStart( revWalk.parseCommit( ref.getObjectId() ));
            }
            System.out.println("Walking all commits starting with " + allRefs.size() + " refs: " + allRefs);
            int count = 0;
            for( RevCommit commit : revWalk ) {
                allCommits.add(commit);
                count++;
            }
            System.out.println("Had " + count + " commits");
        }

        return allCommits;
    }

    // use the following instead to list commits on head branch
    public List<RevCommit> getCommitsFromMaster() throws IOException, GitAPIException {

        List<RevCommit> commitsFromHead = new ArrayList<>();
        ObjectId branchId = this.repo.resolve("HEAD");
        Iterable<RevCommit> commits = this.git.log().add(branchId).call();

        int count = 0;
        for (RevCommit commit : commits) {
                commitsFromHead.add(commit);
                count++;
        }
            System.out.println(count);

        return commitsFromHead;
    }

    //assegna i commit alla release di appartenenza
    public void assignCommitsToReleases(List<RevCommit> commits) {
        for (RevCommit commit : commits) {
            LocalDateTime commitTime = Utils.convertTime(commit.getCommitTime());
            Version currentRelease;
            for (int i = 0; i<releases.size(); i++) {
                if (releases.get(i).getReleaseDate().isAfter(commitTime)) {
                    releases.get(i).addCommit(commit);  //aggiungo il commit alla lista dei commit della versione di appartenenza
                    break;
                }
            }
        }
        setLastCommitFromVersion();
    }

    //essendo i commit ordinati per commitTime decrescente (dal più recente), l'ultimo commit di ogni versione è il primo della lista
    private void setLastCommitFromVersion() {
        for (Version release : releases) {
            List<RevCommit> commits = release.getAllCommits();
            if (commits != null) {
                release.setLastCommit(commits.get(0));
            }
        }
    }

    //assegna ad ogni versione le classi presenti in quella versione (con il corrispettivo stato di queste classi in quella versione)
    //in pratica recupera lo stato del repository in una certa versione
    public void assignClassesToReleases() throws IOException {
        for (Version release : releases) {
            RevCommit lastCommit = release.getLastCommit();
            if (lastCommit != null) {
                Map<String, Class> relClasses = getClasses(lastCommit, release);
                release.setAllClasses(relClasses);
            }
        }
    }


    //recupera le classi appartenenti al repository al momento del commit indicato
    private Map<String, Class> getClasses(RevCommit commit, Version release) throws IOException {

        //recupera tutti i files presenti nel repository al momento del commit indicato
        RevTree tree = commit.getTree();
        Map<String, Class> allClasses = new HashMap<>();

        //il TreeWalk mi permette di navigare tra questi file
        TreeWalk treeWalk = new TreeWalk(this.repo);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);

        while(treeWalk.next()) {
            //considera solo le classi java (esludendo le classi di test)
            if (treeWalk.getPathString().contains(".java") && !treeWalk.getPathString().contains("/test/")) {
                allClasses.put(treeWalk.getPathString(), new Class(treeWalk.getPathString(), new String(this.repo.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8), release));
                System.out.println(treeWalk.getPathString());
            }
        }

        treeWalk.close();
        return allClasses;
    }

    public void calculateFeatures() throws GitAPIException, IOException {
        for (Version release : releases) {
            //calcola le metriche (LOC, churn...) per le classi in ogni release
            retrieveNumLines(release);
            retrieveLocMetrics(release);
            retrieveNumAuthors(release);
        }

    }

    //recupera il numero di linee di codice di ogni classe in una certa release
    private void retrieveNumLines(Version release) {
        if (release.getAllClasses() != null) {
            for (Class javaClass : release.getAllClasses().values()) {
                int numLines = javaClass.getContent().split("[\n|\r]").length;
                javaClass.setSize(numLines);
            }
        }
    }

    private void retrieveLocMetrics(Version release) throws GitAPIException, IOException {
        if (release.getAllCommits() != null) {
            //calcola le linee di codice modificate su tutti i commit per ogni classe
            for (RevCommit commit : release.getAllCommits()) {
                listDiff(commit, release);
            }
            //setta il valor medio di churn per ogni classe
            for (Class javaClass : release.getAllClasses().values()) {
                float sum = javaClass.getChurnArray().stream().mapToInt(Integer::intValue).sum();
                float num = javaClass.getChurnArray().size();
                if (num != 0) {
                    javaClass.setAverageChurn(sum / num);
                }
            }
        }


    }

    public void listDiff(RevCommit commit, Version release) throws GitAPIException, IOException {
        //calcolo le differenze con il commit parent se questo esiste
        if (commit.getParentCount() != 0) {
            String commitId = commit.getId().getName();
            String parentCommit = commitId + "^";

            final List<DiffEntry> diffs = this.git.diff()
                    .setOldTree(prepareTreeParser(this.repo, parentCommit))
                    .setNewTree(prepareTreeParser(this.repo, commitId))
                    .call();

            System.out.println("Found: " + diffs.size() + " differences");
            //ogni oggetto diff mantiene le informazioni sulle modifiche effettuate su un certo file in questo commit (rispetto al precedente)
            for (DiffEntry diff : diffs) {
                if (diff.getNewPath().contains(".java") && !diff.getNewPath().contains("/test/")) {
                    DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                    formatter.setRepository(this.repo);

                    String modifiedClassPath = diff.getNewPath();

                    //recupera la classe modificata tra le classi della release fissata
                    Class modifiedClass = release.getAllClasses().get(modifiedClassPath);

                    //ignora quelle classi che non appartengono allo stato finale del repository nella versione considerata (classi aggiunte e poi rimosse prima del commit finale della versione)
                    if (modifiedClass != null) {

                        //calcola linee di codice aggiunte e rimosse con questa modifica nella classe
                        int addedLines = 0;
                        int deletedLines = 0;
                        for (Edit edit : formatter.toFileHeader(diff).toEditList()) {
                            addedLines += edit.getEndB() - edit.getBeginB();
                            deletedLines += edit.getEndA() - edit.getBeginA();
                        }

                        int locTouched = addedLines + deletedLines;
                        int churn = addedLines - deletedLines;
                        //incremento le metriche di quella classe
                        modifiedClass.incrementLocTouched(locTouched);
                        modifiedClass.incrementLocAdded(addedLines);
                        modifiedClass.incrementLocChurn(churn);

                        if (addedLines >= modifiedClass.getMaxLocAdded()) {
                            modifiedClass.setMaxLocAdded(addedLines);
                        }
                        if (churn >= modifiedClass.getMaxChurn()) {
                            modifiedClass.setMaxChurn(churn);
                        }

                        modifiedClass.getChurnArray().add(churn);

                        modifiedClass.incrementNumRevisions();

                        //inserisce l'autore di questo commit tra gli autori della classe modificata dal commit
                        ArrayList<String> authors = modifiedClass.getAuthors();
                        String author = commit.getAuthorIdent().getName();
                        if (!(authors.contains(author)))  {
                            modifiedClass.getAuthors().add(author);
                        }
                    }
                }
            }
        }
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, String objectId) throws IOException {
        // from the commit we can build the tree which allows us to construct the TreeParser
        //noinspection Duplicates
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(objectId));
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();

            return treeParser;
        }
    }

    private void retrieveNumAuthors(Version release) {
        if (release.getAllClasses() != null) {
            for (Class javaClass : release.getAllClasses().values()) {
                javaClass.setnAuth(javaClass.getAuthors().size());
            }
        }
    }
}
