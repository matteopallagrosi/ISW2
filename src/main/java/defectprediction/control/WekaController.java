package defectprediction.control;

import static java.lang.System.*;

import defectprediction.model.ClassifierEvaluation;
import defectprediction.model.CostSensitive;
import defectprediction.model.FeatureSelection;
import defectprediction.model.Sampling;
import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.CostSensitiveClassifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.supervised.instance.SpreadSubsample;

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

            //simple dataset with no feature selection, no balancing
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
            evaluation.setFeatureSelection(FeatureSelection.NONE);
            evaluation.setSampling(Sampling.NONE);
            evaluation.setCostSensitive(CostSensitive.NONE);
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
            evaluationWithFilter.setFeatureSelection(FeatureSelection.GREEDYSTEPWISE);
            evaluationWithFilter.setSampling(Sampling.NONE);
            evaluationWithFilter.setCostSensitive(CostSensitive.NONE);
            evaluations.add(evaluationWithFilter);


            //BALANCING CON UNDERSAMPLING
            //costruisce il sampling
            SpreadSubsample spreadSubsample = new SpreadSubsample();
            String[] opts = new String[]{ "-M", "1.0"};
            spreadSubsample.setOptions(opts);

            FilteredClassifier fcUnder = new FilteredClassifier();
            fcUnder.setClassifier(classifier);
            fcUnder.setFilter(spreadSubsample);
            fcUnder.buildClassifier(training);

            Evaluation underSamplingEval = new Evaluation(testing);
            underSamplingEval.evaluateModel(fcUnder, testing);

            ClassifierEvaluation evaluationWithUnderSampling = new ClassifierEvaluation(projectName, i, classifierName, underSamplingEval.precision(0), underSamplingEval.recall(0), underSamplingEval.areaUnderROC(0), underSamplingEval.kappa());
            evaluationWithUnderSampling.setTpRate(underSamplingEval.truePositiveRate(0));
            evaluationWithUnderSampling.setFpRate(underSamplingEval.falsePositiveRate(0));
            evaluationWithUnderSampling.setF1(underSamplingEval.fMeasure(0));
            evaluationWithUnderSampling.setFeatureSelection(FeatureSelection.NONE);
            evaluationWithUnderSampling.setSampling(Sampling.UNDERSAMPLING);
            evaluationWithUnderSampling.setCostSensitive(CostSensitive.NONE);
            evaluations.add(evaluationWithUnderSampling);


            //BALANCING CON OVERSAMPLING
            double positiveCount = 0;
            double negativeCount = 0;
            for (int k = 0; k<training.numInstances(); k++) {
                if (training.instance(k).classValue() == 0.0) positiveCount++;
                else negativeCount++;
            }
            if (negativeCount > positiveCount) out.println("OK: " + i);

            Resample resample = new Resample();
            resample.setInputFormat(training);
            resample.setBiasToUniformClass(1.0);
            //dopo il balancing size classe majority = size classe minority
            if ((positiveCount + negativeCount) != 0)
                resample.setSampleSizePercent(2.0*100.0*negativeCount/(positiveCount + negativeCount));

            FilteredClassifier fcOver = new FilteredClassifier();
            fcOver.setClassifier(classifier);
            fcOver.setFilter(resample);
            fcOver.buildClassifier(training);

            Evaluation overSamplingEval = new Evaluation(testing);
            overSamplingEval.evaluateModel(fcOver, testing);

            ClassifierEvaluation evaluationWithOverSampling = new ClassifierEvaluation(projectName, i, classifierName, overSamplingEval.precision(0), overSamplingEval.recall(0), overSamplingEval.areaUnderROC(0), overSamplingEval.kappa());
            evaluationWithOverSampling.setTpRate(overSamplingEval.truePositiveRate(0));
            evaluationWithOverSampling.setFpRate(overSamplingEval.falsePositiveRate(0));
            evaluationWithOverSampling.setF1(overSamplingEval.fMeasure(0));
            evaluationWithOverSampling.setFeatureSelection(FeatureSelection.NONE);
            evaluationWithOverSampling.setSampling(Sampling.OVERSAMPLING);
            evaluationWithOverSampling.setCostSensitive(CostSensitive.NONE);
            evaluations.add(evaluationWithOverSampling);

            //BALANCING CON SMOTE
            SMOTE smote = new SMOTE();
            smote.setInputFormat(training);
            //dopo il balancing size classe majority = size classe minority
            if (negativeCount != 0)
                smote.setPercentage(((negativeCount / positiveCount) * 100.0) - 100.0);

            FilteredClassifier fcSmote = new FilteredClassifier();
            fcSmote.setClassifier(classifier);
            fcSmote.setFilter(smote);
            fcSmote.buildClassifier(training);

            Evaluation smoteEval = new Evaluation(testing);
            smoteEval.evaluateModel(fcSmote, testing);

            ClassifierEvaluation evaluationWithSmote = new ClassifierEvaluation(projectName, i, classifierName, smoteEval.precision(0), smoteEval.recall(0), smoteEval.areaUnderROC(0), smoteEval.kappa());
            evaluationWithSmote.setTpRate(smoteEval.truePositiveRate(0));
            evaluationWithSmote.setFpRate(smoteEval.falsePositiveRate(0));
            evaluationWithSmote.setF1(smoteEval.fMeasure(0));
            evaluationWithSmote.setFeatureSelection(FeatureSelection.NONE);
            evaluationWithSmote.setSampling(Sampling.SMOTE);
            evaluationWithSmote.setCostSensitive(CostSensitive.NONE);
            evaluations.add(evaluationWithSmote);



            //COST-SENSITIVE CLASSIFIER
            CostSensitiveClassifier c1 = new CostSensitiveClassifier();
            c1.setClassifier(classifier);
            c1.setCostMatrix(createCostMatrix(1, 10));
            //se settato a true realizza cost-sensitive classifier (altrimenti fa cost sensitive learning)
            c1.setMinimizeExpectedCost(true);
            c1.buildClassifier(training);

            Evaluation ec1 = new Evaluation(testing, c1.getCostMatrix());
            ec1.evaluateModel(c1, testing);

            ClassifierEvaluation evaluationWithCostSensitive = new ClassifierEvaluation(projectName, i, classifierName, ec1.precision(0), ec1.recall(0), ec1.areaUnderROC(0), ec1.kappa());
            evaluationWithCostSensitive.setTpRate(ec1.truePositiveRate(0));
            evaluationWithCostSensitive.setFpRate(ec1.falsePositiveRate(0));
            evaluationWithCostSensitive.setF1(ec1.fMeasure(0));
            evaluationWithCostSensitive.setFeatureSelection(FeatureSelection.NONE);
            evaluationWithCostSensitive.setSampling(Sampling.NONE);
            evaluationWithCostSensitive.setCostSensitive(CostSensitive.COST_SENSITIVE_CLASSIFIER);
            evaluations.add(evaluationWithCostSensitive);
        }
    }

    private CostMatrix createCostMatrix(double weightFalsePositive, double
            weightFalseNegative) {
        CostMatrix costMatrix = new CostMatrix(2);
        costMatrix.setCell(0, 0, 0.0);
        costMatrix.setCell(1, 0, weightFalsePositive);
        costMatrix.setCell(0, 1, weightFalseNegative);
        costMatrix.setCell(1, 1, 0.0);
        return costMatrix;
    }

    public static void printProbabilities(Classifier classifier, Instances training, Instances testing) throws Exception {
        int numtesting = testing.numInstances();
        out.println("There are " + numtesting + " test instances");

        classifier.buildClassifier(training);

        // Loop over each test instance.
        for (int i = 0; i < numtesting; i++) {
            // Get the true class label from the instance's own classIndex.
            //ritorna il valore che l'istanza i-esima ha nel test set
            String trueClassLabel =
                    testing.instance(i).toString(testing.classIndex());

            // Make the prediction here.
            double predictionIndex =
                    classifier.classifyInstance(testing.instance(i));

            // Get the predicted class label from the predictionIndex.
            String predictedClassLabel =
                    testing.classAttribute().value((int) predictionIndex);

            // Get the prediction probability distribution.
            //ritorna la probabilità che l'instanza i-esima appartenga a ciascuna delle classi possibili (in questo caso 2)
            double[] predictionDistribution =
                    classifier.distributionForInstance(testing.instance(i));

            // Print out the true label, predicted label, and the distribution.
            out.printf("%5d: true=%-10s, predicted=%-10s, distribution=",
                    i, trueClassLabel, predictedClassLabel);

            // Loop over all the prediction labels in the distribution.
            //in questo caso abbiamo due possibili classi quindi due probabilità
            for (int predictionDistributionIndex = 0;
                 predictionDistributionIndex < predictionDistribution.length;
                 predictionDistributionIndex++) {
                // Get this distribution index's class label.
                String predictionDistributionIndexAsClassLabel =
                        testing.classAttribute().value(
                                predictionDistributionIndex);

                // Get the probability.
                double predictionProbability =
                        predictionDistribution[predictionDistributionIndex];

                out.printf("[%10s : %6.3f]",
                        predictionDistributionIndexAsClassLabel,
                        predictionProbability);
            }
            out.println();
        }
    }

    public void printEvaluationsToCsv(String projName) throws IOException {
        FileWriter fileWriter = null;
        //Name of CSV for output
        String outname = projName + "evaluations.csv";
        try {
            fileWriter = new FileWriter(outname);
            fileWriter.append("Dataset, #TrainingReleases, Classifier, FeatureSelection, Balancing, CostSensitive, TruePositiveRate, FalsePositiveRate, Precision, Recall, AUC, Kappa, FMeasure");
            fileWriter.append("\n");
            for (ClassifierEvaluation evaluation : evaluations) {
                fileWriter.append(evaluation.getProjName());
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getNumTrainingReleases()));
                fileWriter.append(",");
                fileWriter.append(evaluation.getClassifier());
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getFeatureSelection()));
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getSampling()));
                fileWriter.append(",");
                fileWriter.append(String.valueOf(evaluation.getCostSensitive()));
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
