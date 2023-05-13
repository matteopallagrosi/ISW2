package defectprediction.control;

import defectprediction.Utils;
import defectprediction.model.Version;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class JiraController {

    private String projectName;
    private HashMap<LocalDateTime, String> releaseNames;
    private HashMap<LocalDateTime, String> releaseID;
    private ArrayList<LocalDateTime> releases;

    public JiraController(String name) {
        this.projectName = name;
    }

    public List<Version> getReleaseInfo() throws IOException {
        List<Version> allReleases = new ArrayList<>();
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

}


