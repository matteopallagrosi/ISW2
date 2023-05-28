package defectprediction.control;

import defectprediction.Main;
import defectprediction.Utils;
import defectprediction.model.Ticket;
import defectprediction.model.Version;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JiraController {

    private HashMap<LocalDateTime, String> releaseNames;
    private HashMap<LocalDateTime, String> releaseID;
    private ArrayList<LocalDateTime> releases;
    private double coldStartP;
    private static final int NUM_PROJECT = 5;

    public List<Version> getReleaseInfo(String projectName) throws IOException {
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

        List<Version> allReleases = new ArrayList<>();
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
    public List<Ticket> getFixTicket(String projName, List<Version> allReleases) throws IOException {
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
            int deletedTicket = 0;
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
                    affectedVersions.add(getVersionFromId(id, allReleases));
                }
                LocalDateTime creation = convertStringToDate(creationDate);
                LocalDateTime resolution = convertStringToDate(resolutionDate);
                Version openingVersion = getVersionFromDate(creation, allReleases);
                Version fixVersion = getVersionFromDate(resolution, allReleases);
                Version injectedVersion = (!affectedVersions.isEmpty()) ? affectedVersions.get(0) : null;

                //escludiamo i ticket in cui IV = FV (ossia i difetti che non sono post-release) oppure in cui IV > OV (non è possibile che un difetto
                // sia stato individuato dopo essere stato fixato)
                if ((injectedVersion != null) && ((fixVersion.getIndex() == injectedVersion.getIndex()) || (injectedVersion.getIndex() > openingVersion.getIndex()))) {
                    deletedTicket++;
                }
                else {
                    Ticket ticket = new Ticket(key, creation, resolution, openingVersion, fixVersion, affectedVersions, injectedVersion);
                    fixTickets.add(ticket);
                    fixVersion.getFixTickets().add(ticket);
                }
            }
            System.out.println("deleted tickets: " + deletedTicket);
        } while (i < total);
        return fixTickets;
    }

    //recupera la versione di riferimento per una certa data
    private Version getVersionFromDate(LocalDateTime date, List<Version> allReleases) {
        Version currentVersion = allReleases.get(0);
        for (int i = 0; i<allReleases.size(); i++) {
            if (allReleases.get(i).getReleaseDate().isAfter(date)) {
                currentVersion =  allReleases.get(i);
                break;
            }
        }
        return currentVersion;
    }

    //recupera la versione dato un certo id (identificativo univoco per le versioni su JIRA)
    private Version getVersionFromId(long id, List<Version> allReleases) {
        Version currentVersion = allReleases.get(0);
        for (int i = 0; i<allReleases.size(); i++) {
            if (allReleases.get(i).getId() == id) {
                currentVersion = allReleases.get(i);
                break;
            }
        }
        return currentVersion;
    }

    private Version getVersionFromIndex(long index, List<Version> allReleases) {
        Version currentVersion = allReleases.get(0);
        for (int i = 0; i<allReleases.size(); i++) {
            if (allReleases.get(i).getIndex() == index) {
                currentVersion = allReleases.get(i);
                break;
            }
        }
        return currentVersion;
    }

    //calcolo l'injected version per i ticket che non la presentano
    public void assignInjectedInversion(List<Version> releases, List<Ticket> tickets) throws IOException {
        for (Version release : releases) {
            //calcola proportion per una certa versione
            release.setProportion(calculateProportion(release, releases));
            //applica il proportion calcolato a tutti i ticket di questa versione
            for (Ticket ticket : release.getFixTickets()) {
                if (ticket.getInjectedVersion() == null) {
                    double difference = ticket.getFixVersion().getIndex() - ticket.getOpeningVersion().getIndex();
                    if (difference == 0) difference = 1;
                    double injectedIndex = ticket.getFixVersion().getIndex() - (difference * ticket.getFixVersion().getProportion());
                    System.out.println(ticket.getFixVersion().getProportion());
                    System.out.println(ticket.getKey() + " p = " + injectedIndex);
                    long index = Double.valueOf(Math.ceil(injectedIndex)).longValue();
                    System.out.println(index);
                    Version injectedVersion = getVersionFromIndex(index, releases);
                    ticket.setInjectedVersion(injectedVersion);
                }
            }
        }
    }

    //calcola il valore di proportion da utilizzare per il calcolo di IV per i ticket con fixVersion = currentVersion e IV mancante
    //se nelle versioni [1, currentVersion-1] ci sono meno di 5 ticket utilizza ProportionColdStart, altrimenti ProportionIncrement
    private double calculateProportion(Version currentVersion, List<Version> allReleases) throws IOException {
        int count = 0;
        double proportion = 0;
        //conta quanti ticket sono disponibili nelle versioni [1, currentVersion-1]
        for (Version release : allReleases) {
            if (release.getId() == currentVersion.getId()) {
                break;
            }
            count += release.getFixTickets().size();
        }
        if (count<5) {
            proportion = coldStartP;
        }
        else {
            proportion = calculateProportionIncrement(currentVersion, allReleases);
        }
        return proportion;
    }

    //per una certa versione R calcola P_Increment come il P medio tra i difetti fixati nelle versioni R-1.
    //Il valore ottenuto viene utilizzato per calcolare l'injected version di tutti i ticket con fixVersion = R.
    //quando questa funzione viene invocata sulla versione R si suppone che proportion sia stato calcolato per le versioni da 1 a R-1
    //quindi i ticket in queste versioni avranno tutti un injected version
    // P = (FV-IV)/(FV-OV)
    private double calculateProportionIncrement(Version currentVersion, List<Version> allReleases) {
        double sum = 0.0;
        double numTickets = 0.0;
        for (Version release : allReleases) {
            //le release sono ordinate per data di release (dalla meno recente alla più recente), quando incontra la currentVersion interrompe il ciclo
            if (release.getId() == currentVersion.getId()) {
                break;
            }
            for (Ticket ticket : release.getFixTickets()) {
                double fixIndex = ticket.getFixVersion().getIndex();
                double injectedIndex = ticket.getInjectedVersion().getIndex();
                double openingIndex = ticket.getOpeningVersion().getIndex();
                double difference = fixIndex - openingIndex;
                //se FV=OV poniamo FV-OV pari a 0 per evitare la divisione per 0
                if (difference == 0) {
                    difference = 1;
                }
                double proportion = (fixIndex - injectedIndex) / difference;
                if (proportion >= 1) {
                    sum += proportion;
                    numTickets += 1;
                }
            }
        }
        return (numTickets != 0) ? sum /numTickets : 1;
    }

    public void calculateProportionColdStart() throws IOException {
        List<Double> medium = new ArrayList<>();
        //esegue il coldStart su NUM_PROJECT progetti
        for (int i = 0; i < NUM_PROJECT; i++) {
            System.out.println("sto calcolando coldStart per il progetto " + i);
            Properties p = new Properties();
            String project;
            //recupero i ticket di altri progetti con cui fare coldStart
            try (InputStream is = (Objects.requireNonNull(Main.class.getResource("/config" + i + ".properties"))).openStream()) {
                p.load(is);
                project = p.getProperty("projectName");
            }
            //recupero i ticket
            List<Version> allReleases = getReleaseInfo(project);
            List<Ticket> tickets = getFixTicket(project, allReleases);

            double sum = 0.0;
            double numTickets = 0.0;
            for (Ticket fixTicket : tickets) {
                if (fixTicket.getInjectedVersion() == null) continue;
                double fixIndex = fixTicket.getFixVersion().getIndex();
                double injectedIndex = fixTicket.getInjectedVersion().getIndex();
                double openingIndex = fixTicket.getOpeningVersion().getIndex();
                double difference = fixIndex - openingIndex;
                //se FV=OV poniamo FV-OV pari a 0 per evitare la divisione per 0
                if (difference == 0) {
                    difference = 1;
                }
                double proportion = (fixIndex - injectedIndex) / difference;
                sum += proportion;
                numTickets += 1;
            }

            medium.add(sum / numTickets);
            System.out.println("sum del progetto " + i + " " + sum);
            System.out.println("num tickets del progetto " + i + " " + numTickets);
        }

        medium.sort(Double::compareTo);
        //ritorna la mediana tra i valori di proportion calcolati per ciascuno dei progetti
        coldStartP =  medium.get((NUM_PROJECT-1)/2);
        System.out.println("calcolato coldStart = " + coldStartP);
    }
}


