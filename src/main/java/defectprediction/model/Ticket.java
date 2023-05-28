package defectprediction.model;

import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Ticket {

    private String key;
    private Version injectedVersion;
    private List<Version> affectedVersion;
    private Version openingVersion;
    private Version fixVersion;
    private LocalDateTime creationDate;      //utilizzata per determinare l'opening version
    private LocalDateTime resolutionDate;    //utilizzata per determinare la fix version
    private List<RevCommit> fixCommits = new ArrayList<>();      //commits associati a questi ticket

    public Ticket(String key, LocalDateTime creationDate, LocalDateTime resolutionDate, Version openingVersion, Version fixVersion, List<Version> affectedVersions, Version injectedVersion) {
        this.key = key;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;
        this.openingVersion = openingVersion;
        this.fixVersion = fixVersion;
        this.affectedVersion = affectedVersions;
        this.injectedVersion = injectedVersion;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Version getInjectedVersion() {
        return injectedVersion;
    }

    public void setInjectedVersion(Version injectedVersion) {
        this.injectedVersion = injectedVersion;
    }

    public List<Version> getAffectedVersion() {
        return affectedVersion;
    }

    public void setAffectedVersion(List<Version> affectedVersion) {
        this.affectedVersion = affectedVersion;
    }

    public Version getOpeningVersion() {
        return openingVersion;
    }

    public void setOpeningVersion(Version openingVersion) {
        this.openingVersion = openingVersion;
    }

    public Version getFixVersion() {
        return fixVersion;
    }

    public void setFixVersion(Version fixVersion) {
        this.fixVersion = fixVersion;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public LocalDateTime getResolutionDate() {
        return resolutionDate;
    }

    public void setResolutionDate(LocalDateTime resolutionDate) {
        this.resolutionDate = resolutionDate;
    }

    public List<RevCommit> getCommits() {
        return fixCommits;
    }

    public void setCommits(List<RevCommit> commits) {
        this.fixCommits = commits;
    }
}
