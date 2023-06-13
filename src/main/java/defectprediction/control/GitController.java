package defectprediction.control;

import static java.lang.System.*;

import defectprediction.Utils;
import defectprediction.model.Class;
import defectprediction.model.Ticket;
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

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

public class GitController {

    private Repository repo;
    private Git git;
    private List<Version> releases;  //le releases del repository ordinate per releaseDate crescente
    private List<Ticket> tickets;    //i ticket su Jira associati al repository
    private static final String CLASS_PATH = ".java";
    private static final String TEST_DIR = "/test/";

    public GitController(Repository repo, List<Version> versions, List<Ticket> tickets) {
        this.repo = repo;
        git = new Git(repo);
        this.releases = versions;
        this.tickets = tickets;
    }

    public void createDataset(String projectName) throws GitAPIException, IOException {
        List<RevCommit> commits = getCommitsFromMaster();
        assignCommitsToReleases(commits);
        checkEmptyReleases();
        assignClassesToReleases();
        calculateFeatures();
        setFixCommits();
        setBuggyClasses();
        deleteLastReleases();
        printDatasetToCsv(projectName);
        printCsvWalkForward(projectName);
        createArffDatasets(projectName);
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
            out.println("Walking all commits starting with " + allRefs.size() + " refs: " + allRefs);
            int count = 0;
            for( RevCommit commit : revWalk ) {
                allCommits.add(commit);
                count++;
            }
            out.println("Had " + count + " commits");
        }

        return allCommits;
    }

    // use the following instead to list commits on head branch
    private List<RevCommit> getCommitsFromMaster() throws IOException, GitAPIException {

        List<RevCommit> commitsFromHead = new ArrayList<>();
        ObjectId branchId = this.repo.resolve("HEAD");
        Iterable<RevCommit> commits = this.git.log().add(branchId).call();

        int count = 0;
        for (RevCommit commit : commits) {
                commitsFromHead.add(commit);
                count++;
        }
            out.println(count);

        return commitsFromHead;
    }

    //assegna i commit alla release di appartenenza
    private void assignCommitsToReleases(List<RevCommit> commits) {
        for (RevCommit commit : commits) {
            LocalDateTime commitTime = Utils.convertTime(commit.getCommitTime());
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

    //verifica la presenza di release con zero commit associati, e, se presenti, elimina le release vuote e shifta gli indici delle release
    private void checkEmptyReleases() {
        Iterator<Version> i = releases.iterator();
        while (i.hasNext()) {
            Version currentRelease = i.next(); // must be called before you can call i.remove()
            out.println("release n. " + currentRelease.getIndex());
            // Do something
            if (currentRelease.getAllCommits() == null) {
                shiftReleaseIndexes(currentRelease.getIndex());
                i.remove();
            }
        }
    }

    private void shiftReleaseIndexes(int index) {
        for (Version release : releases) {
            if (release.getIndex() > index) release.setIndex(release.getIndex() - 1);
        }
    }

    //assegna ad ogni versione le classi presenti in quella versione (con il corrispettivo stato di queste classi in quella versione)
    //in pratica recupera lo stato del repository in una certa versione
    private void assignClassesToReleases() throws IOException {
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
            if (treeWalk.getPathString().contains(CLASS_PATH) && !treeWalk.getPathString().contains(TEST_DIR)) {
                allClasses.put(treeWalk.getPathString(), new Class(treeWalk.getPathString(), new String(this.repo.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8), release));
                out.println(treeWalk.getPathString());
            }
        }

        treeWalk.close();
        return allClasses;
    }

    private void calculateFeatures() throws GitAPIException, IOException {
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

    //ritorna la lista dei path delle classi modificate dal commit
    private List<String> getClassesFromCommit(RevCommit commit) throws GitAPIException, IOException {
        List<String> modifiedClassPaths = new ArrayList<>();  //path delle classi modificate dal commit
        if (commit.getParentCount() == 0) return modifiedClassPaths;

        String commitId = commit.getId().getName();
        String parentCommit = commitId + "^";

        final List<DiffEntry> diffs = this.git.diff()
                .setOldTree(prepareTreeParser(this.repo, parentCommit))
                .setNewTree(prepareTreeParser(this.repo, commitId))
                .call();

        //recupera la lista delle modifiche effettuate rispetto al commit precedente, ogni modifica è relativa ad un certo file
        for (DiffEntry diff : diffs) {
            if (diff.getNewPath().contains(CLASS_PATH) && !diff.getNewPath().contains(TEST_DIR)) {
                //recupera il path della classe modificata
                String modifiedClassPath = diff.getNewPath();
                modifiedClassPaths.add(modifiedClassPath);
            }
        }

        return modifiedClassPaths;
    }

    private void listDiff(RevCommit commit, Version release) throws GitAPIException, IOException {
        //calcolo le differenze con il commit parent se questo esiste
        if (commit.getParentCount() == 0) return;
        String commitId = commit.getId().getName();
        String parentCommit = commitId + "^";

        final List<DiffEntry> diffs = this.git.diff()
                .setOldTree(prepareTreeParser(this.repo, parentCommit))
                .setNewTree(prepareTreeParser(this.repo, commitId))
                .call();

        //ogni oggetto diff mantiene le informazioni sulle modifiche effettuate su un certo file in questo commit (rispetto al precedente)
        for (DiffEntry diff : diffs) {
            if (diff.getNewPath().contains(CLASS_PATH) && !diff.getNewPath().contains(TEST_DIR)) {
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
                    List<String> authors = modifiedClass.getAuthors();
                    String author = commit.getAuthorIdent().getName();
                    if (!(authors.contains(author)))  {
                        modifiedClass.getAuthors().add(author);
                    }

                    if(modifiedClass.getPath().equals("hedwig-server/src/main/java/org/apache/hedwig/server/common/UnexpectedError.java") && (release.getIndex() == 1)) {
                        out.println(commitId);
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


    private void setFixCommits() {
        int count = 0;
        for (Ticket ticket : tickets) {
            if (assignCommitToTicket(ticket)) {
                count++;
            }
        }
        out.println(count);
    }


    //assegna il fix commit che riporta l'id del ticket (qualora il commit non fosse presente il ticket viene scartato)
    private boolean assignCommitToTicket(Ticket fixTicket) {
        boolean found = false;
        for (Version release : releases) {
            if (release.getAllCommits() != null) {
                for (RevCommit fixCommit : release.getAllCommits()) {
                    String fixMessage = fixTicket.getKey() + ":";
                    if (fixCommit.getFullMessage().contains(fixMessage)) {
                        fixTicket.getCommits().add(fixCommit);
                        found = true;
                    }
                }
            }
        }
        return found;
    }

    private void setBuggyClasses() throws GitAPIException, IOException {
        for (Ticket ticket : tickets) {
            //scarta i ticket che hanno IV = FV, perchè siamo interessati solo ai difetti post-release (IV<FV)
            if (ticket.getInjectedVersion().getId() == ticket.getFixVersion().getId()) continue;
            //recupera i fix commit associati al ticket
            for (RevCommit fixCommit : ticket.getCommits())  {
                //recupera il path delle classi modificate dal commit (ignorando le classi di test)
                List<String> classPaths = getClassesFromCommit(fixCommit);
                //recupera tutte le versioni tra IV (inclusa) e FV(esclusa) indicate sul ticket
                //etichetta come buggy le classi modificate dal commit come in queste versioni
                Version injectedVersion = ticket.getInjectedVersion();
                Version fixVersion = ticket.getFixVersion();
                setNumFixes(classPaths, fixVersion);
                if (!classPaths.isEmpty()) {
                    labelClasses(classPaths, injectedVersion, fixVersion);
                }
            }
        }
    }

    private void labelClasses(List<String>  classPaths, Version injectedVersion, Version fixVersion) {
        //itera fino all'injected version e si ferma
        for (Version version : releases) {
            if (version.getIndex() == fixVersion.getIndex()) break;
            if (version.getIndex() >= injectedVersion.getIndex() && version.getIndex() < fixVersion.getIndex()) {
                for (String modifiedClass : classPaths){
                    Class modifclass = version.getAllClasses().get(modifiedClass);
                    if (modifclass!= null) {
                        //setta la classe come buggy in una certa versione
                        modifclass.setBuggy(true);
                    }
                }
            }
        }
    }

    //incrementa di 1 il numero di fix delle classi associate a un fix commit nella fix version (conta solo i fix dei difetti post-release)
    private void setNumFixes(List<String> classPaths, Version fixVersion) {
        for (String classPath : classPaths) {
            Class modifiedClass = fixVersion.getAllClasses().get(classPath);
            if (modifiedClass != null) modifiedClass.addFix();
        }
    }

    //per ridurre lo snoring viene rimossa metà delle release (le più recenti)
    private void deleteLastReleases() {
        int numReleases = releases.size();
        releases.removeIf(currentRelease -> currentRelease.getIndex() > numReleases / 2);
    }

    private void printCsvWalkForward(String projName) throws IOException {
        //crea due csv per ogni release del progetto (a partire dalla seconda release), uno conterrà il set di dati per il training, l'altro per il setting, secondo un approccio walkForward
        for (int i = 1; i < releases.size(); i++) { //l'indice i tiene traccia della creazione di training e testing set usati nell'i-esima iterazione del walkForward
            String trainingName = projName + "training_" + i + ".csv";
            String testingName = projName + "testing_" + i + ".csv";
            try (FileWriter trainingWriter = new FileWriter(trainingName);
                 FileWriter testingWriter = new FileWriter(testingName)) {
                //Name of CSV for output
                trainingWriter.append("LOC,LOC_touched,NR,NFix,NAuth,LOC_added,MAX_LOC_added,Churn,MAX_Churn,AVG_Churn,Buggy");
                trainingWriter.append("\n");
                for (int j = 0; j < i; j++) {
                        for (Class javaClass : releases.get(j).getAllClasses().values()) {
                            trainingWriter.append(String.valueOf(javaClass.getSize()));
                            trainingWriter.append(",");
                            trainingWriter.append(String.valueOf(javaClass.getLocTouched()));
                            trainingWriter.append(",");
                            trainingWriter.append(String.valueOf(javaClass.getNr()));
                            trainingWriter.append(",");
                            trainingWriter.append(String.valueOf(javaClass.getnFix()));
                            trainingWriter.append(",");
                            trainingWriter.append(String.valueOf(javaClass.getnAuth()));
                            trainingWriter.append(",");
                            trainingWriter.append(String.valueOf(javaClass.getLocAdded()));
                            trainingWriter.append(",");
                            trainingWriter.append(String.valueOf(javaClass.getMaxLocAdded()));
                            trainingWriter.append(",");
                            trainingWriter.append(String.valueOf(javaClass.getChurn()));
                            trainingWriter.append(",");
                            trainingWriter.append(String.valueOf(javaClass.getMaxChurn()));
                            trainingWriter.append(",");
                            trainingWriter.append(String.valueOf(javaClass.getAverageChurn()));
                            trainingWriter.append(",");
                            if (javaClass.isBuggy()) trainingWriter.append("Yes");
                            else trainingWriter.append("No");
                            trainingWriter.append("\n");
                        }
                }

                testingWriter.append("LOC,LOC_touched,NR,NFix,NAuth,LOC_added,MAX_LOC_added,Churn,MAX_Churn,AVG_Churn,Buggy");
                testingWriter.append("\n");
                for (Class javaClass : releases.get(i).getAllClasses().values()) {
                    testingWriter.append(String.valueOf(javaClass.getSize()));
                    testingWriter.append(",");
                    testingWriter.append(String.valueOf(javaClass.getLocTouched()));
                    testingWriter.append(",");
                    testingWriter.append(String.valueOf(javaClass.getNr()));
                    testingWriter.append(",");
                    testingWriter.append(String.valueOf(javaClass.getnFix()));
                    testingWriter.append(",");
                    testingWriter.append(String.valueOf(javaClass.getnAuth()));
                    testingWriter.append(",");
                    testingWriter.append(String.valueOf(javaClass.getLocAdded()));
                    testingWriter.append(",");
                    testingWriter.append(String.valueOf(javaClass.getMaxLocAdded()));
                    testingWriter.append(",");
                    testingWriter.append(String.valueOf(javaClass.getChurn()));
                    testingWriter.append(",");
                    testingWriter.append(String.valueOf(javaClass.getMaxChurn()));
                    testingWriter.append(",");
                    testingWriter.append(String.valueOf(javaClass.getAverageChurn()));
                    testingWriter.append(",");
                    if (javaClass.isBuggy()) testingWriter.append("Yes");
                    else testingWriter.append("No");
                    testingWriter.append("\n");
                }
            }
        }
    }

    private void printDatasetToCsv(String projName) throws IOException {
        String outname = projName + "dataset.csv";
        try (FileWriter fileWriter = new FileWriter(outname)) {
            //Name of CSV for output
            fileWriter.append("Version,File Name,LOC,LOC_touched,NR,NFix,NAuth,LOC_added,MAX_LOC_added,Churn,MAX_Churn,AVG_Churn,Buggy");
            fileWriter.append("\n");
            for (Version release : releases) {
                if (release.getAllClasses() != null) {
                    for (Class javaClass : release.getAllClasses().values()) {
                        fileWriter.append(String.valueOf(javaClass.getVersion().getIndex()));
                        fileWriter.append(",");
                        fileWriter.append(javaClass.getPath());
                        fileWriter.append(",");
                        fileWriter.append(String.valueOf(javaClass.getSize()));
                        fileWriter.append(",");
                        fileWriter.append(String.valueOf(javaClass.getLocTouched()));
                        fileWriter.append(",");
                        fileWriter.append(String.valueOf(javaClass.getNr()));
                        fileWriter.append(",");
                        fileWriter.append(String.valueOf(javaClass.getnFix()));
                        fileWriter.append(",");
                        fileWriter.append(String.valueOf(javaClass.getnAuth()));
                        fileWriter.append(",");
                        fileWriter.append(String.valueOf(javaClass.getLocAdded()));
                        fileWriter.append(",");
                        fileWriter.append(String.valueOf(javaClass.getMaxLocAdded()));
                        fileWriter.append(",");
                        fileWriter.append(String.valueOf(javaClass.getChurn()));
                        fileWriter.append(",");
                        fileWriter.append(String.valueOf(javaClass.getMaxChurn()));
                        fileWriter.append(",");
                        fileWriter.append(String.valueOf(javaClass.getAverageChurn()));
                        fileWriter.append(",");
                        if (javaClass.isBuggy()) fileWriter.append("Yes");
                        else fileWriter.append("No");
                        fileWriter.append("\n");
                    }
                }
            }
        }
    }

    private void createArffDatasets(String projName) throws IOException {
        for (int i = 1; i < releases.size(); i++) {
            String filename = projName + "training_" + i + ".csv";
            Utils.convertCsvToArff(filename);
        }
        for (int i = 1; i < releases.size(); i++) {
            String filename = projName + "testing_" + i + ".csv";
            Utils.convertCsvToArff(filename);
        }
    }


}
