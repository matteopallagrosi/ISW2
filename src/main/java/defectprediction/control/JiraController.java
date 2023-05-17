package defectprediction.control;

import defectprediction.Utils;
import defectprediction.model.Ticket;
import defectprediction.model.Version;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JiraController {

    private String projectName;
    private HashMap<LocalDateTime, String> releaseNames;
    private HashMap<LocalDateTime, String> releaseID;
    private ArrayList<LocalDateTime> releases;
    List<Version> allReleases = new ArrayList<>();

    public JiraController(String name) {
        this.projectName = name;
    }

    public List<Version> getReleaseInfo() throws IOException {
        String url = "https://issues.apache.org/jira/rest/api/2/project/" + projectName;
        JSONObject json = Utils.readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");
        releaseNames = new HashMap<>();
        releaseID = new HashMap<> ();
        releases = new ArrayList<>();
        for (int i = 0; i < versions.length(); i++ ) {
            String name = "";
            String id = "";
            if(versions.getJSONObject(i).has("releaseDate")) {
                if (versions.getJSONObject(i).has("name"))
                    name = versions.getJSONObject(i).get("name").toString();
                if (versions.getJSONObject(i).has("id"))
                    id = versions.getJSONObject(i).get("id").toString();
                addRelease(versions.getJSONObject(i).get("releaseDate").toString(), name,id);
            }
        }

        // order releases by date
        //@Override
        releases.sort(LocalDateTime::compareTo);

        //considera i progetti che hanno almeno 6 release
        if (releases.size() < 6) {
            //TODO
        }


        int numVersions = releases.size();
        for (int i = 0; i < numVersions; i++) {
            int index = i + 1;
            int id = Integer.parseInt(releaseID.get(releases.get(i)));
            String name = releaseNames.get(releases.get(i));
            LocalDateTime releaseDate = releases.get(i);
            Version version = new Version(index, id, name, releaseDate);
            allReleases.add(version);
        }

        return allReleases;
    }

    private void addRelease(String strDate, String name, String id) {
        LocalDate date = LocalDate.parse(strDate);
        LocalDateTime dateTime = date.atStartOfDay();
        if (!releases.contains(dateTime))
            releases.add(dateTime);
        releaseNames.put(dateTime, name);
        releaseID.put(dateTime, id);
    }

    private LocalDateTime convertStringToDate(String strDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        return LocalDateTime.parse(strDate, formatter);
    }

    //questo metodo recupera tutti i ticket su JIRA per il progetto scelto tali che Type == “defect” AND (status == “Closed” OR status
    //==“Resolved”) AND Resolution ==“Fixed”. Per ogni ticket recupera opening version (OV), Fix version (OV) utilizzando le date di creazione e
    //risoluzione del ticket (sempre presenti), mentre le affected version (AV), e quindi l'injected version (IV), sono recuperate quando presenti.
    public List<Ticket> getFixTicket(String projName) throws IOException {
        List<Ticket> fixTickets = new ArrayList<>();
        int j = 0;
        int i = 0;
        int total = 1;
        //Get JSON API for closed bugs w/ AV in the project
        do {
            //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                    + projName + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                    + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                    + i + "&maxResults=" + j;
            JSONObject json = Utils.readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            for (; i < total && i < j; i++) {
                //Iterate through each bug
                String key = issues.getJSONObject(i%1000).get("key").toString();
                JSONObject issuesObject = issues.getJSONObject(i%1000).getJSONObject("fields");
                String creationDate = issuesObject.get("created").toString();
                String resolutionDate = issuesObject.get("resolutiondate").toString();
                JSONArray versions = issuesObject.getJSONArray("versions");
                List<Version> affectedVersions = new ArrayList<>();
                for (int k = 0; k < versions.length(); k++) {
                    String affectedVersion = versions.getJSONObject(k).get("id").toString();
                    int id = Integer.parseInt(affectedVersion);
                    affectedVersions.add(getVersionFromId(id));
                }
                LocalDateTime creation = convertStringToDate(creationDate);
                LocalDateTime resolution = convertStringToDate(resolutionDate);
                Version openingVersion = getVersionFromDate(creation);
                Version fixVersion = getVersionFromDate(resolution);
                Version injectedVersion = (!affectedVersions.isEmpty()) ? affectedVersions.get(0) : null;
                Ticket ticket = new Ticket(key, creation, resolution, openingVersion, fixVersion, affectedVersions, injectedVersion);
                fixTickets.add(ticket);
            }
        } while (i < total);
        return fixTickets;
    }


    //recupera la versione di riferimento per una certa data
    private Version getVersionFromDate(LocalDateTime date) {
        Version currentVersion = null;
        for (int i = 0; i<allReleases.size(); i++) {
            if (allReleases.get(i).getReleaseDate().isAfter(date)) {
                currentVersion = allReleases.get(i);
                break;
            }
        }
        return currentVersion;
    }

    //recupera la versione dato un certo id (identificativo univoco per le versioni su JIRA)
    private Version getVersionFromId(long id) {
        Version currentVersion = null;
        for (int i = 0; i<allReleases.size(); i++) {
            if (allReleases.get(i).getId() == id) {
                currentVersion = allReleases.get(i);
                break;
            }
        }
        return currentVersion;

    }

}


