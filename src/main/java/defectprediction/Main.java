package defectprediction;

import defectprediction.control.GitController;
import defectprediction.control.JiraController;
import defectprediction.model.Ticket;
import defectprediction.model.Version;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class Main {

    public static void main(String[] args) throws IOException, GitAPIException {
        Properties p = new Properties();
        String repoPath;
        String projectName;
        try (InputStream is = (Objects.requireNonNull(Main.class.getResource("/config.properties"))).openStream()) {
            p.load(is);
            repoPath = p.getProperty("repository");
            projectName = p.getProperty("projectName");
        }
        Repository repo = new FileRepositoryBuilder().
            setGitDir(new File(repoPath))
            .readEnvironment()
            .findGitDir()
            .build();

        JiraController jiraInfo = new JiraController();

        //recupera tutte le releases del progetto (con index, id, nome, releaseDate)
        List<Version> releases = jiraInfo.getReleaseInfo(projectName);
        //recupera i fix ticket associati al progetti
        List<Ticket> tickets = jiraInfo.getFixTicket("BOOKKEEPER", releases);

        jiraInfo.calculateProportionColdStart();
        jiraInfo.assignInjectedInversion(releases);

        GitController gitInfo= new GitController(repo, releases,tickets);

        gitInfo.createDataset(projectName);
    }
}
