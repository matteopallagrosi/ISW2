package defectprediction.model;

public class ClassifierEvaluation {

    private String projName;
    private int numTrainingReleases;
    private String classifier;
    private double tpRate;
    private double fpRate;
    private double precision;
    private double recall;
    private double auc;
    private double kappa;
    private double f1;
    private FeatureSelection featureSelection;
    private Sampling sampling;
    private CostSensitive costSensitive;

    public ClassifierEvaluation(String projName, int numReleases, String classifierName, double precision, double recall, double auc, double kappa) {
        this.projName = projName;
        this.numTrainingReleases = numReleases;
        this.classifier = classifierName;
        this.precision = precision;
        this.recall = recall;
        this.auc = auc;
        this.kappa = kappa;
    }

    public String getProjName() {
        return projName;
    }

    public void setProjName(String projName) {
        this.projName = projName;
    }

    public int getNumTrainingReleases() {
        return numTrainingReleases;
    }

    public void setNumTrainingReleases(int numTrainingReleases) {
        this.numTrainingReleases = numTrainingReleases;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public double getPrecision() {
        return precision;
    }

    public void setPrecision(double precision) {
        this.precision = precision;
    }

    public double getRecall() {
        return recall;
    }

    public void setRecall(double recall) {
        this.recall = recall;
    }

    public double getAuc() {
        return auc;
    }

    public void setAuc(double auc) {
        this.auc = auc;
    }

    public double getKappa() {
        return kappa;
    }

    public void setKappa(double kappa) {
        this.kappa = kappa;
    }

    public double getTpRate() {
        return tpRate;
    }

    public void setTpRate(double tpRate) {
        this.tpRate = tpRate;
    }

    public double getFpRate() {
        return fpRate;
    }

    public void setFpRate(double fpRate) {
        this.fpRate = fpRate;
    }

    public double getF1() {
        return f1;
    }

    public void setF1(double f1) {
        this.f1 = f1;
    }

    public FeatureSelection getFeatureSelection() {
        return featureSelection;
    }

    public void setFeatureSelection(FeatureSelection featureSelection) {
        this.featureSelection = featureSelection;
    }

    public Sampling getSampling() {
        return sampling;
    }

    public void setSampling(Sampling sampling) {
        this.sampling = sampling;
    }

    public CostSensitive getCostSensitive() {
        return costSensitive;
    }

    public void setCostSensitive(CostSensitive costSensitive) {
        this.costSensitive = costSensitive;
    }
}
