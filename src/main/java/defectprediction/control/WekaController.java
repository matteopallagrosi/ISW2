package defectprediction.control;

import defectprediction.model.ClassifierEvaluation;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WekaController {

    private List<ClassifierEvaluation> evaluations = new ArrayList<>();

    public void simpleEvaluation(String projectName, int numReleases) throws Exception {
        doWalkForward(projectName, "Random Forest", numReleases);
        doWalkForward(projectName, "NaiveBayes", numReleases);
        doWalkForward(projectName, "Ibk", numReleases);
        printEvaluationsToCsv(projectName);
    }

    public void doWalkForward(String projectName, String classifierName, int numReleases) throws Exception {
        Classifier classifier = switch (classifierName) {
            case "Random Forest" -> new RandomForest();

            case "NaiveBayes" -> new NaiveBayes();

            case "Ibk" -> new IBk();

            default -> new RandomForest();
        };

        //nell'i-esima iterazione del walkForward, il training test contiene fino alla release i, il testing set è costituito dalla release i + 1
        for (int i = 1; i < numReleases; i++) {
            String trainingSet = projectName + "training_" + i + ".arff";
            DataSource trainingSource = new DataSource(trainingSet);
            Instances training = trainingSource.getDataSet();
            String testingSet = projectName + "testing_" + i + ".arff";
            DataSource testingSource = new DataSource(testingSet);
            Instances testing = testingSource.getDataSet();
            System.out.println(trainingSet);
            System.out.println(testingSet);

            int numAttr = training.numAttributes();
            training.setClassIndex(numAttr - 1);
            testing.setClassIndex(numAttr - 1);


            classifier.buildClassifier(training);

            Evaluation eval = new Evaluation(testing);

            eval.evaluateModel(classifier, testing);

            evaluations.add(new ClassifierEvaluation(projectName, i, classifierName, eval.truePositiveRate(0), eval.falsePositiveRate(0), eval.precision(0), eval.recall(0), eval.areaUnderROC(0), eval.kappa(), eval.fMeasure(0)));
        }
    }

    public void printEvaluationsToCsv(String projName) throws IOException {
        FileWriter fileWriter = null;
        try {
            String outname = projName + "evaluations.csv";
            //Name of CSV for output
            fileWriter = new FileWriter(outname);
            fileWriter.append("Dataset, #TrainingReleases, Classifier, TruePositiveRate, FalsePositiveRate, Precision, Recall, AUC, Kappa, FMeasure");
            fileWriter.append("\n");
            for (ClassifierEvaluation evaluation : evaluations) {
                fileWriter.append(evaluation.getProjName());
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getNumTrainingReleases()));
                fileWriter.append(",");
                fileWriter.append(evaluation.getClassifier());
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getTpRate()));
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getFpRate()));
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getPrecision()));
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getRecall()));
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getAuc()));
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getKappa()));
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getF1()));
                fileWriter.append("\n");
            }
        } finally {
            fileWriter.close();
        }
    }
}
