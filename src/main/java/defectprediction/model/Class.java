package defectprediction.model;

import java.util.ArrayList;
import java.util.List;

public class Class {

    private String name;
    private String path;
    private String content;
    private Version version;

    //features values
    private int size;           //size = LOC = lines of code
    private int locTouched;     //sum over revisions of LOC added + deleted
    private short nr;           //number of revisions (commit on this class in a specific version)
    private short nFix;         //number of bug fixes
    private int nAuth;        //number of authors
    private int locAdded;       //sum over revisions of LOC added
    private int maxLocAdded;    //maximum over revisions of LOC added
    private int churn;          //sum over revisions of added-deleted LOC
    private int maxChurn;       //maximum over revisions of LOC added
    private float averageChurn;   //average churn over revisions

    private List<Integer> churnArray = new ArrayList<>();
    private ArrayList<String> authors = new ArrayList<>();

    public Class(String path, String content, Version release)  {
        this.path = path;
        this.content = content;
        this.version = release;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getLocTouched() {
        return locTouched;
    }

    public void setLocTouched(int locTouched) {
        this.locTouched = locTouched;
    }

    public short getNr() {
        return nr;
    }

    public void setNr(short nr) {
        this.nr = nr;
    }

    public short getnFix() {
        return nFix;
    }

    public void setnFix(short nFix) {
        this.nFix = nFix;
    }

    public int getnAuth() {
        return nAuth;
    }

    public void setnAuth(int nAuth) {
        this.nAuth = nAuth;
    }

    public int getLocAdded() {
        return locAdded;
    }

    public void setLocAdded(int locAdded) {
        this.locAdded = locAdded;
    }

    public int getMaxLocAdded() {
        return maxLocAdded;
    }

    public void setMaxLocAdded(int maxLocAdded) {
        this.maxLocAdded = maxLocAdded;
    }

    public int getChurn() {
        return churn;
    }

    public void setChurn(int churn) {
        this.churn = churn;
    }

    public int getMaxChurn() {
        return maxChurn;
    }

    public void setMaxChurn(int maxChurn) {
        this.maxChurn = maxChurn;
    }

    public float getAverageChurn() {
        return averageChurn;
    }

    public void setAverageChurn(float averageChurn) {
        this.averageChurn = averageChurn;
    }

    public void incrementLocTouched(int lines) {
        locTouched += lines;
    }

    public void incrementLocAdded(int lines) {
        locAdded += lines;
    }

    public void incrementLocChurn(int lines) {
        churn += lines;
    }

    public List<Integer> getChurnArray() {
        return churnArray;
    }

    public void setChurnArray(List<Integer> churnArray) {
        this.churnArray = churnArray;
    }

    public void incrementNumRevisions() {
        nr += 1;
    }

    public ArrayList<String> getAuthors() {
        return authors;
    }

    public void setAuthors(ArrayList<String> authors) {
        this.authors = authors;
    }
}
