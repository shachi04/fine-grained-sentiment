package edu.ucsc.cs;
import edu.umd.cs.psl.application.inference.LazyMPEInference;
import edu.umd.cs.psl.application.inference.MPEInference;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxPseudoLikelihood;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.LazyMaxLikelihoodMPE;
import edu.umd.cs.psl.application.learning.weight.random.GroundSliceRandOM;
import edu.umd.cs.psl.application.learning.weight.maxmargin.MaxMargin;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import edu.umd.cs.psl.application.learning.weight.maxmargin.PositiveMinNormProgram;
//import edu.umd.cs.psl.application.learning.weight.em.HardEM;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.VotedPerceptron;
import edu.umd.cs.psl.application.learning.weight.random.FirstOrderMetropolisRandOM
import edu.umd.cs.psl.application.learning.weight.random.HardEMRandOM
import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.database.DataStore
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.database.ResultList
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type
import edu.umd.cs.psl.evaluation.result.FullInferenceResult
import edu.umd.cs.psl.evaluation.statistics.DiscretePredictionComparator
import edu.umd.cs.psl.evaluation.statistics.DiscretePredictionStatistics
import edu.umd.cs.psl.evaluation.statistics.SimpleRankingComparator
import edu.umd.cs.psl.groovy.PSLModel;
import edu.umd.cs.psl.groovy.PredicateConstraint;
import edu.umd.cs.psl.groovy.SetComparison;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.ui.functions.textsimilarity.*
import edu.umd.cs.psl.ui.loading.InserterUtils;
import edu.umd.cs.psl.util.database.Queries;
import edu.umd.cs.psl.evaluation.statistics.RankingScore;
import edu.umd.cs.psl.evaluation.statistics.filter.MaxValueFilter


/*
 * Config bundle changed to accept String as UniqueID
 */
ConfigManager cm = ConfigManager.getManager()
ConfigBundle config = cm.getBundle("fine-grained")

File file3 = new File("~/Documents/Shachi/CMPS209C/reviews/Results/unigram_negation_contrast/results.csv");
filename4 = "~/Documents/Shachi/CMPS209C/reviews/Results/unigram_negation_contrast/auc.csv"
/* Uses H2 as a DataStore and stores it in a temp. directory by default */
def defaultPath = System.getProperty("java.io.tmpdir")
String dbpath = config.getString("dbpath", defaultPath + File.separator + "fine-grained")
DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbpath, true), config)

/*
 * Initialize PSL model
 */
PSLModel m = new PSLModel(this, data)


/*
 * Predicates
 */
m.add predicate: "contrast" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "noncontrast", types:[ArgumentType.UniqueID, ArgumentType.UniqueID]

m.add predicate: "priorpos", types: [ArgumentType.UniqueID]
m.add predicate: "priorneg", types: [ArgumentType.UniqueID]
m.add predicate: "subjectivitypos", types: [ArgumentType.UniqueID]
m.add predicate: "subjectivityneg", types: [ArgumentType.UniqueID]
m.add predicate: "possentiment", types: [ArgumentType.UniqueID]
m.add predicate: "negsentiment", types: [ArgumentType.UniqueID]
m.add predicate: "nrclexiconpos", types: [ArgumentType.UniqueID]
m.add predicate: "nrclexiconneg", types: [ArgumentType.UniqueID]
m.add predicate: "unigrampos", types: [ArgumentType.UniqueID]
m.add predicate: "unigramneg", types: [ArgumentType.UniqueID]

m.add predicate: "all", types: [ArgumentType.UniqueID]


/*
 * Adding rules
 */

/*
 * Rules for attribute features alone - sentiment lexicons as source
 */
m.add rule : (possentiment(A) ) >> ~negsentiment(A), weight :5
m.add rule : (negsentiment(A) ) >> ~possentiment(A), weight :5

m.add rule : (priorpos(A) ) >> possentiment(A), weight :5
m.add rule : (priorneg(A) ) >> negsentiment(A), weight :5

m.add rule : subjectivitypos(A) >> possentiment(A), weight : 5
m.add rule : subjectivityneg(A) >> negsentiment(A), weight : 5

m.add rule : nrclexiconpos(A) >> possentiment(A), weight : 5
m.add rule : nrclexiconneg(A) >> negsentiment(A), weight : 5

m.add rule : unigrampos(A) >> possentiment(A), weight : 5
m.add rule : unigramneg(A) >> negsentiment(A), weight : 5

/*
 * Relational feature - contrast and non-contrast
 */

// m.add rule : (contrast(A,B) & possentiment(A) & ( B^A)) >> negsentiment(B)  , weight :50
// m.add rule : (contrast(A,B) & negsentiment(A) & ( B^A)) >> possentiment(B)  , weight :50
//

m.add rule : (contrast(A,B) & possentiment(B) ) >> negsentiment(A)  , weight :10
m.add rule : (contrast(A,B) & negsentiment(B) ) >> possentiment(A)  , weight :10

m.add rule : (noncontrast(A,B) & possentiment(B) ) >> possentiment(A)  , weight :10
m.add rule : (noncontrast(A,B) & negsentiment(B) ) >> negsentiment(A)  , weight :10


/*
 * loading the predicates from the data files
 */
int folds = 10
List<Partition> trainPartition = new ArrayList<Partition>(folds)
List<Partition> trueDataPartition = new ArrayList<Partition>(folds)
List<Partition> testDataPartition = new ArrayList<Partition>(folds)
List<Partition> trueTestDataPartition = new ArrayList<Partition>(folds)

/*
 * Initialize partitions for all cross validation sets
 */
for(cvSet =0 ;cvSet<10;++cvSet)
{
	trainPartition.add(cvSet, new Partition(cvSet))
	trueDataPartition.add(cvSet, new Partition(cvSet + folds))
	testDataPartition.add(cvSet, new Partition(cvSet + 2*folds))
	trueTestDataPartition.add(cvSet, new Partition(cvSet + 3*folds))
}

/*
 * The results are shown for all threshold levels.
 */
thresholdList = [0.5,0.45,0.4,0.3]

/*
 * There is some issue with the cross validation looping code, so currently have to set each cvSet manually and run for each fold.
 */

//for(cvSet =0 ;cvSet<10;++cvSet)
//{

/*
 * Set the cross validation fold set
 */
cvSet = 9
/*
 * Set the folder to write into
 */
folder = (cvSet+10)%10;
if (folder ==0) folder = 10
filename1 = "~/Documents/Shachi/CMPS209C/reviews/Results/unigram_negation_contrast/fold"+folder+"/possentiment.csv"
filename2 = "~/Documents/Shachi/CMPS209C/reviews/Results/unigram_negation_contrast/fold"+folder+"/negsentiment.csv"


File file1 = new File(filename1);
File file2 = new File(filename2);
File file4 = new File(filename4);

/*
 * Train data partition, each partition has 9 folders, one kept aside for testing... 
 * 
 * loading the predicates from the data files into the trainPartition
 */
for (trainSet = 1 ; trainSet<=9;++trainSet)
{
	dirToUse = 0;
	dirToUse = (cvSet+trainSet)%10
	if(dirToUse==0) dirToUse = 10;


	filename = 'data'+java.io.File.separator+'sentiment'+java.io.File.separator+'fold'+dirToUse+
			java.io.File.separator;
	InserterUtils.loadDelimitedDataTruth(data.getInserter(nrclexiconpos, trainPartition.get(cvSet)),
			filename+"NRC_negation_pos.csv","\t");
	InserterUtils.loadDelimitedDataTruth(data.getInserter(nrclexiconneg, trainPartition.get(cvSet)),
			filename+"NRC_negation_neg.csv","\t");

	InserterUtils.loadDelimitedDataTruth(data.getInserter(subjectivitypos, trainPartition.get(cvSet)),
			filename+"subjectivity_pos.csv");
	InserterUtils.loadDelimitedDataTruth(data.getInserter(subjectivityneg, trainPartition.get(cvSet)),
			filename+"subjectivity_neg.csv");

	InserterUtils.loadDelimitedDataTruth(data.getInserter(unigrampos, trainPartition.get(cvSet)),
			filename+"unigram_pos_negation.csv","\t");
	InserterUtils.loadDelimitedDataTruth(data.getInserter(unigramneg, trainPartition.get(cvSet)),
			filename+"unigram_neg_negation.csv","\t");

	InserterUtils.loadDelimitedDataTruth(data.getInserter(priorpos, trainPartition.get(cvSet)),
			filename+"wordnet_negation_flipall_softpos.csv","\t");
	InserterUtils.loadDelimitedDataTruth(data.getInserter(priorneg, trainPartition.get(cvSet)),
			filename+"wordnet_negation_flipall_softneg.csv","\t");

	InserterUtils.loadDelimitedData(data.getInserter(all, trainPartition.get(cvSet)), filename+"allID.csv");

	InserterUtils.loadDelimitedData(data.getInserter(contrast, trainPartition.get(cvSet)),
			filename+"contrast_ids.csv");
	InserterUtils.loadDelimitedData(data.getInserter(noncontrast, trainPartition.get(cvSet)),
			filename+"noncontrast_ids.csv");
	/*
	 * Load in the ground truth positive and negative segments
	 */
	InserterUtils.loadDelimitedData(data.getInserter(negsentiment, trueDataPartition.get(cvSet)),
			filename+"trueneg_other.csv");
	InserterUtils.loadDelimitedData(data.getInserter(possentiment, trueDataPartition.get(cvSet)),
			filename+"truepos_other.csv");
}


/*
 * For test data partition - it needs only one fold in each partition.... Start with 10,1,2,3.... so on.
 */
testSet = 0;
testSet = (cvSet+10)%10
if(testSet==0) testSet = 10;
filename = 'data'+java.io.File.separator+'sentiment'+java.io.File.separator+'fold'+testSet+java.io.File.separator;


InserterUtils.loadDelimitedDataTruth(data.getInserter(subjectivitypos,
		testDataPartition.get(cvSet)), filename+"subjectivity_pos.csv");

InserterUtils.loadDelimitedDataTruth(data.getInserter(subjectivityneg,
		testDataPartition.get(cvSet)), filename+"subjectivity_neg.csv");

InserterUtils.loadDelimitedDataTruth(data.getInserter(unigrampos, testDataPartition.get(cvSet)),
		filename+"unigram_pos_negation.csv","\t");
InserterUtils.loadDelimitedDataTruth(data.getInserter(unigramneg, testDataPartition.get(cvSet)),
		filename+"unigram_neg_negation.csv","\t");


InserterUtils.loadDelimitedDataTruth(data.getInserter(nrclexiconpos, testDataPartition.get(cvSet)),
		filename+"NRC_negation_pos.csv","\t");
InserterUtils.loadDelimitedDataTruth(data.getInserter(nrclexiconneg, testDataPartition.get(cvSet)),
		filename+"NRC_negation_neg.csv","\t");

InserterUtils.loadDelimitedDataTruth(data.getInserter(priorpos, testDataPartition.get(cvSet)),
		filename+"wordnet_negation_flipall_softpos.csv","\t");
InserterUtils.loadDelimitedDataTruth(data.getInserter(priorneg, testDataPartition.get(cvSet)),
		filename+"wordnet_negation_flipall_softneg.csv","\t");

InserterUtils.loadDelimitedData(data.getInserter(all, testDataPartition.get(cvSet)), filename+"allID.csv");

InserterUtils.loadDelimitedData(data.getInserter(contrast, testDataPartition.get(cvSet)),
		filename+"contrast_ids.csv");
InserterUtils.loadDelimitedData(data.getInserter(noncontrast, testDataPartition.get(cvSet)),
		filename+"noncontrast_ids.csv");

/*
 * Load in the ground truth positive and negative segments
 */
InserterUtils.loadDelimitedData(data.getInserter(possentiment, trueTestDataPartition.get(cvSet)),
		filename+"truepos_other.csv");

InserterUtils.loadDelimitedData(data.getInserter(negsentiment, trueTestDataPartition.get(cvSet)),
		filename+"trueneg_other.csv");


Database trainDB = data.getDatabase(trainPartition.get(cvSet),
		[Contrast, Noncontrast,Priorpos, Priorneg,Unigrampos, Unigramneg,
			Nrclexiconneg,Nrclexiconpos,Subjectivityneg,Subjectivitypos, All] as Set);

ResultList allGroundings1 = trainDB.executeQuery(Queries.getQueryForAllAtoms(contrast))
println "groundings for contrast" +allGroundings1.size();

/*
 * Setting the predicates possentiment and negsentiment to an initial value for all groundings
 */

ResultList allGroundings = trainDB.executeQuery(Queries.getQueryForAllAtoms(all))
println "groundings for all"+ allGroundings.size();
for (j = 0; j < allGroundings.size(); j++) {
	GroundTerm [] grounding = allGroundings.get(j)
	RandomVariableAtom atom1 = trainDB.getAtom(possentiment, grounding);
	RandomVariableAtom atom2 = trainDB.getAtom(negsentiment, grounding);
	atom1.setValue(0.0);
	atom2.setValue(0.0);
	atom1.commitToDB();
	atom2.commitToDB();
}

MPEInference inferenceApp = new MPEInference(m,trainDB, config)

inferenceApp.mpeInference();
inferenceApp.close();
println "trudatapartition : "+trueDataPartition.get(cvSet)
Database trueDataDB = data.getDatabase(trueDataPartition.get(cvSet), [possentiment,negsentiment] as Set);
MaxLikelihoodMPE weightLearning = new MaxLikelihoodMPE(m, trainDB, trueDataDB, config);

weightLearning.learn();
weightLearning.close();
/*
 * Newly learned weights
 */

println ( "Learned model:\n")
println (m)


/*Test database setup*/

Database testDB = data.getDatabase(testDataPartition.get(cvSet),
		[Contrast, Noncontrast, Priorpos, Priorneg,Unigrampos, Unigramneg,Nrclexiconneg,Nrclexiconpos,Subjectivityneg,
			Subjectivitypos, All] as Set);
ResultList groundings = testDB.executeQuery(Queries.getQueryForAllAtoms(all))
print groundings.size();
for (j = 0; j < groundings.size(); j++) {
	GroundTerm [] grounding = groundings.get(j)
	RandomVariableAtom atom1 = testDB.getAtom(possentiment, grounding);
	RandomVariableAtom atom2 = testDB.getAtom(negsentiment, grounding);
	atom1.setValue(0.0);
	atom2.setValue(0.0);
	atom1.commitToDB();
	atom2.commitToDB();
}
inferenceApp = new MPEInference(m, testDB,config)
inferenceApp.mpeInference();
inferenceApp.close();




println "test results";
//file1.append("Partition:" + testDataPartition.get(cvSet)+"\n")
count = 0
println "Inference results with hand-defined weights:"
for (GroundAtom atom : Queries.getAllAtoms(testDB, possentiment)){
	//		println atom.toString() + "\t" + atom.getValue();
	file1.append(atom.toString().substring(atom.toString().indexOf('(')+1
			,atom.toString().indexOf(')')) + "\t" + atom.getValue()+"\n");
	count = count+1;
}
println count

count = 0
//file2.append("Partition:" + testDataPartition.get(cvSet)+"\n")
println "Inference results with hand-defined weights:"
for (GroundAtom atom : Queries.getAllAtoms(testDB, negsentiment))
{
	//		println atom.toString() + "\t" + atom.getValue();
	file2.append(atom.toString().substring(atom.toString().indexOf('(')+1
			,atom.toString().indexOf(')') ) + "\t" + atom.getValue()+"\n");
	count = count + 1
}
println count
println "Truetestdatapartition "+trueTestDataPartition.get(cvSet)
Database trueTestDB = data.getDatabase(trueTestDataPartition.get(cvSet), [possentiment, negsentiment] as Set);



Set<GroundAtom> groundings1 = Queries.getAllAtoms(trueTestDB, possentiment)
int totalPosTestExamples = groundings1.size()
println "possentiment total: "+totalPosTestExamples

groundings1 = Queries.getAllAtoms(trueTestDB, negsentiment)
int totalNegTestExamples = groundings1.size()
println "negsentiment total: "+totalNegTestExamples

//file4.append("Testfold" +"\t"+ "sentiment" +"\t"+ "AUPRC" +"\t"+ "NEGAUPRC"+"\t"+"AreaROC \n")
def comparator = new SimpleRankingComparator(testDB)
comparator.setBaseline(trueTestDB)

// Choosing what metrics to report
def metrics = [ RankingScore.AUPRC, RankingScore.NegAUPRC,  RankingScore.AreaROC]
double [] score = new double[metrics.size()]
double [] score2 = new double[metrics.size()]
try {
	for (j = 0; j < metrics.size(); j++) {
		comparator.setRankingScore(metrics.get(j))
		score[j] = comparator.compare(possentiment)
		score2[j] = comparator.compare(negsentiment)
	}
	file4.append(testSet +"\t"+ "possentiment" +"\t"+ score[0] +"\t"+ score[1]+"\t"+score[2]+"\n")
	file4.append(testSet +"\t"+ "negsentiment" +"\t"+ score2[0] +"\t"+ score2[1]+"\t"+score2[2]+"\n")
	//	file3.append("\nArea under positive-class PR curve: " + score[0]+"\n")
	//	file3.append("Area under negetive-class PR curve: " + score[1]+"\n")
	//	file3.append("Area under ROC curve: " + score[2]+"\n")
}
catch (ArrayIndexOutOfBoundsException e) {
	System.out.println("No evaluation data! Terminating!");
}
//comparator.setBaseline(trueTestDB)
//// Choosing what metrics to report
//try {
//	for (j = 0; j < metrics.size(); j++) {
//		comparator.setRankingScore(metrics.get(j))
//
//	}
//
////	file3.append("\nArea under positive-class PR curve: " + score2[0]+"\n")
////	file3.append("Area under negetive-class PR curve: " + score2[1]+"\n")
////	file3.append("Area under ROC curve: " + score2[2]+"\n")
////	println "Written neg AUC to file3!! "
//}
//catch (ArrayIndexOutOfBoundsException e) {
//	System.out.println("No evaluation data! Terminating!");
//}

Set<GroundAtom> groundings3 = Queries.getAllAtoms(trueTestDB, possentiment)
int totalPosTestExamples3 = groundings3.size()

groundings3 = Queries.getAllAtoms(trueTestDB, negsentiment)
int totalNegTestExamples3 = groundings3.size()

Set<GroundAtom> groundings2 = Queries.getAllAtoms(trueDataDB, possentiment)
int totalPosTrainExamples = groundings2.size()

groundings2 = Queries.getAllAtoms(trueDataDB, negsentiment)
int totalNegTrainExamples = groundings2.size()

int total =  totalNegTrainExamples+totalPosTestExamples3+totalNegTestExamples3+totalPosTrainExamples
println "Total ###"+total
println "Pos ###"+totalPosTrainExamples
println "Ned ###"+totalNegTrainExamples



/*
 * Accuracy
 */

groundings1 = Queries.getAllAtoms(trueTestDB, possentiment)
totalPosTestExamples = groundings1.size()
println "printing totalTestExamples:Possentiment"+totalPosTestExamples
groundings2 = Queries.getAllAtoms(trueTestDB, negsentiment)
totalNegTestExamples = groundings2.size()
println "printing totalTestExamples: Negsentiment"+totalNegTestExamples

DiscretePredictionStatistics stats;
accuracy = 0
f1 = 0
p = 0
r = 0
//file3.append("CVSet"+"\t"+"Pol"+"\t"+"Th"+"\t"+"Accuracy"+"\t"+"F1"+"\t"+"Precision"+"\t"+"Recall"+"\n")

poscomparator = new DiscretePredictionComparator(testDB)
poscomparator.setBaseline(trueTestDB)
poscomparator.setResultFilter(new MaxValueFilter(possentiment, 1))

for(threshold in thresholdList)
{

	poscomparator.setThreshold(threshold) // treat best value as true as long as it is nonzero

	stats = poscomparator.compare(possentiment, totalNegTestExamples+totalPosTestExamples)
	accuracy = stats.getAccuracy()
	f1 = stats.getF1(DiscretePredictionStatistics.BinaryClass.POSITIVE)
	p = stats.getPrecision(DiscretePredictionStatistics.BinaryClass.POSITIVE)
	r = stats.getRecall(DiscretePredictionStatistics.BinaryClass.POSITIVE)
	file3.append(cvSet+"\t"+"pos"+"\t"+threshold+"\t"+accuracy+"\t"+f1+"\t"+p+"\t"+r+"\n")
}


negcomparator = new DiscretePredictionComparator(testDB)
negcomparator.setBaseline(trueTestDB)
negcomparator.setResultFilter(new MaxValueFilter(negsentiment, 1))

for (threshold in thresholdList)
{
	negcomparator.setThreshold(threshold) // treat best value as true as long as it is nonzero
	stats = negcomparator.compare(negsentiment, totalNegTestExamples+totalPosTestExamples)
	accuracy = stats.getAccuracy()
	f1 = stats.getF1(DiscretePredictionStatistics.BinaryClass.POSITIVE)
	p = stats.getPrecision(DiscretePredictionStatistics.BinaryClass.POSITIVE)
	r = stats.getRecall(DiscretePredictionStatistics.BinaryClass.POSITIVE)
	file3.append(cvSet+"\t"+"neg"+"\t"+threshold+"\t"+accuracy+"\t"+f1+"\t"+p+"\t"+r+"\n")
}


trueDataDB.close();

trainDB.close();
testDB.close();
trueTestDB.close();
//}