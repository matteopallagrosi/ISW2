package defectprediction.control;

import defectprediction.Utils;
import defectprediction.model.Class;
import defectprediction.model.Version;
import jdk.jshell.execution.Util;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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


    //recupera le classi appartenenti al repository al momento del commit indicato
    public List<Class> getClasses(RevCommit commit) throws IOException {

        //recupera tutti i files presenti nel repository al momento del commit indicato
        RevTree tree = commit.getTree();
        List<Class> allClasses = new ArrayList<Class>();

        //il TreeWalk mi permette di navigare tra questi file
        TreeWalk treeWalk = new TreeWalk(this.repo);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);

        while(treeWalk.next()) {
            allClasses.add(new Class(treeWalk.getPathString(), new String( this.repo.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8)));
            System.out.println(treeWalk.getPathString());
        }

        treeWalk.close();
        return allClasses;
    }


}
