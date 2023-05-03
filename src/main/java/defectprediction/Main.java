package defectprediction;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import java.io.File;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException, GitAPIException {
        Repository repo = new FileRepositoryBuilder().
            setGitDir(new File("C:\\Users\\HP\\Desktop\\bookkeeper\\.git"))
            .build();

        Git git = new Git(repo);

        Iterable<RevCommit> log = git.log().call();
        RevCommit commit = log.iterator().next();
        System.out.println(commit.getFullMessage());
    }

}
