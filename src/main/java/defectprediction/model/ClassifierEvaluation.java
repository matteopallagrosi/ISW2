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

    public ClassifierEvaluation(String projName, int numReleases, String classifierName, double tpRate, double fpRate, double precision, double recall, double auc, double kappa, double fMeasure) {
        this.projName = projName;
        this.numTrainingReleases = numReleases;
        this.classifier = classifierName;

        this.tpRate = tpRate;
        this.fpRate = fpRate;
        //if (!Double.isNaN(precision))
            this.precision = precision;
        //if (!Double.isNaN(recall))
            this.recall = recall;
        //if (!Double.isNaN(auc))
            this.auc = auc;
        //if (!Double.isNaN(kappa))
            this.kappa = kappa;
            this.f1 = fMeasure;
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
}
