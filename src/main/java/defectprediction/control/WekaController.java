package defectprediction.control;

import static java.lang.System.*;

import defectprediction.model.ClassifierEvaluation;
import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;

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
        for (int i = 2; i < numReleases; i++) {

            //dataset with no feature selection
            String trainingSet = projectName + "training_" + i + ".arff";
            DataSource trainingSource = new DataSource(trainingSet);
            Instances training = trainingSource.getDataSet();
            String testingSet = projectName + "testing_" + i + ".arff";
            DataSource testingSource = new DataSource(testingSet);
            Instances testing = testingSource.getDataSet();
            out.println(trainingSet);
            out.println(testingSet);

            int numAttr = training.numAttributes();
            training.setClassIndex(numAttr - 1);
            testing.setClassIndex(numAttr - 1);


            classifier.buildClassifier(training);

            Evaluation eval = new Evaluation(testing);

            eval.evaluateModel(classifier, testing);

            ClassifierEvaluation evaluation = new ClassifierEvaluation(projectName, i, classifierName, eval.precision(0), eval.recall(0), eval.areaUnderROC(0), eval.kappa());
            evaluation.setTpRate(eval.truePositiveRate(0));
            evaluation.setFpRate(eval.falsePositiveRate(0));
            evaluation.setF1(eval.fMeasure(0));
            evaluation.setFeatureSelection(false);
            evaluations.add(evaluation);



            //dataset con FEATURE SELECTION (FILTER APPROACH CON GREEDY STEPWISE COME SEARCH ENGINE)
            AttributeSelection filter = new AttributeSelection();
            CfsSubsetEval evalSubset = new CfsSubsetEval();
            GreedyStepwise search = new GreedyStepwise();
            search.setSearchBackwards(true);
            filter.setEvaluator(evalSubset);
            filter.setSearch(search);

            filter.setInputFormat(training);

            //applica il filtro al training e al testing set
            Instances filteredTraining = Filter.useFilter(training, filter);
            Instances filteredTesting = Filter.useFilter(testing, filter);

            int numAttrFiltered = filteredTraining.numAttributes();

            classifier.buildClassifier(filteredTraining);

            //valutazione sui set filtrati
            filteredTraining.setClassIndex(numAttrFiltered - 1);
            filteredTesting.setClassIndex(numAttrFiltered - 1);
            Evaluation filteredEval = new Evaluation(filteredTesting);
            filteredEval.evaluateModel(classifier, filteredTesting);

            ClassifierEvaluation evaluationWithFilter = new ClassifierEvaluation(projectName, i, classifierName, filteredEval.precision(0), filteredEval.recall(0), filteredEval.areaUnderROC(0), filteredEval.kappa());
            evaluationWithFilter.setTpRate(filteredEval.truePositiveRate(0));
            evaluationWithFilter.setFpRate(filteredEval.falsePositiveRate(0));
            evaluationWithFilter.setF1(filteredEval.fMeasure(0));
            evaluationWithFilter.setFeatureSelection(true);
            evaluations.add(evaluationWithFilter);

        }
    }

    public void printEvaluationsToCsv(String projName) throws IOException {
        FileWriter fileWriter = null;
        //Name of CSV for output
        String outname = projName + "evaluations.csv";
        try {
            fileWriter = new FileWriter(outname);
            fileWriter.append("Dataset, #TrainingReleases, Classifier, FeatureSelection, TruePositiveRate, FalsePositiveRate, Precision, Recall, AUC, Kappa, FMeasure");
            fileWriter.append("\n");
            for (ClassifierEvaluation evaluation : evaluations) {
                fileWriter.append(evaluation.getProjName());
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getNumTrainingReleases()));
                fileWriter.append(",");
                fileWriter.append(evaluation.getClassifier());
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.isFeatureSelection()));
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
            if (fileWriter != null) fileWriter.close();
        }
    }
}
