package edu.macalester.moviedatabase;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;

import edu.cmu.lti.lexical_db.ILexicalDatabase;
import edu.cmu.lti.lexical_db.NictWordNet;
import edu.cmu.lti.ws4j.impl.JiangConrath;
import edu.cmu.lti.ws4j.util.WS4JConfiguration;


public class Main {
	static int threads  = 12;

	public static void main(String[] args) {
		CollaborativeDatabase db = new CollaborativeDatabase();
		db.initializeMovieLensTags("ml-10M100K/tags.dat");
		try {
			generateTagSimilarityCSV(db, new CollaborativeMatching(db), "collab_matching.csv");
			generateTagSimilarityCSV(db, new CollaborativeMutualInformation(db), "collab_MI.csv");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		DistributionalDatabase ddb = new DistributionalDatabase();
		ddb.initializeMovieLensTags("ml-10M100K/tags.dat");
		try {
			generateTagSimilarityCSV(ddb, new DistributionalMutualInformation(ddb), "dist_MI.csv");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		ProjectionalDatabase pdb = new ProjectionalDatabase();
		pdb.initializeMovieLensTags("ml-10M100K/tags.dat");
		try {
			generateTagSimilarityCSV(pdb, new DistributionalMatching(pdb), "dist_matching.csv");
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Kendalls Tau for collaborative matching:");
		tauBetweenCSVandWordnet("collab_matching.csv");
		System.out.println("Kendalls Tau for collaborative MI:");
		tauBetweenCSVandWordnet("collab_MI.csv");
		System.out.println("Kendalls Tau for distributional matching:");
		tauBetweenCSVandWordnet("dist_matching.csv");
		System.out.println("Kendalls Tau for distributional MI:");
		tauBetweenCSVandWordnet("dist_MI.csv");
	}
	
	public static void tauBetweenCSVandWordnet(String file){
		
		WS4JConfiguration.getInstance().setMFS(true);
        ILexicalDatabase db = new NictWordNet();
		final JiangConrath rc = new JiangConrath(db);
		
		final ArrayList<Double> distMatchingSimilarities  = new ArrayList<Double>();
		final ArrayList<Double> wordnetSimilarities = new ArrayList<Double>();
		
		java.util.List<String> lines = null;
		try {
			lines = Files.readAllLines(Paths.get(file), Charset.defaultCharset());
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		ParallelForEach.loop(lines, threads, new Procedure<String>() {
			public void call(String line){
				String[] column = line.split(",");
				 double jc = rc.calcRelatednessOfWords(column[0].replace("\"", "").replace(" ", "") , column[1].replace("\"", "").replace(" ", ""));
				 if(jc != 0.0){
					 synchronized (distMatchingSimilarities) {
						 distMatchingSimilarities.add(Double.parseDouble(column[2]));
						 wordnetSimilarities.add(jc);
					 }
				 }
			}
		});
		
	    System.out.println(KendallsCorrelation.correlation(distMatchingSimilarities, wordnetSimilarities));
	}
	
	public static void generateTagSimilarityCSV(Database database, final TagSimilarityMeasure similarityMeasure, String outputFile) throws IOException{
		
		FileWriter fWriter = null;
		try {
			fWriter = new FileWriter(outputFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		fWriter.append('"' + " Tag 1 " + '"'+ ',' + '"' + " Tag 2 " + '"' + " , " + "Similarity");
		fWriter.append('\n');
		
		final LinkedList<String> tags = new LinkedList<String>(database.getTagsSet());
								
		final FileWriter writer = fWriter;
		ParallelForEach.loop(tags,
				threads,
				new Procedure<String>() {
                    @Override
                    public void call(String comparingTag) throws Exception {
                    	int start = tags.indexOf(comparingTag);
                    	for(String comparedTag : tags.subList(start+1, tags.size() )){
							
            				double cc = similarityMeasure.calculateSimilarity(comparingTag, comparedTag);
            				
            				if(cc != 0){
            					
            					// Remove newlines, commas and apostrophes that may distort the CSV file when being written.
            					synchronized(writer){
            					writer.append("\"" + comparingTag.replace("\"", "").replace("\n", "").replace(",", "") + '"'+ ',' + '"' + comparedTag.replace("\"", "").replace("\n", "").replace(",", "") + '"' + "," + cc+"\n");
            					}           						
            						
            						
            				}
            				
            			}
                    }
                }
				);

		fWriter.flush();
	    fWriter.close();
		
	}

}
