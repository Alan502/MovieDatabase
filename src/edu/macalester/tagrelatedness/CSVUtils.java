package edu.macalester.tagrelatedness;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.search.similarities.Similarity;

import edu.cmu.lti.jawjaw.pobj.POS;
import edu.cmu.lti.ws4j.WS4J;

public class CSVUtils {
	
	public static void generateTagSimilarityCSV(LinkedList<String> tagsList, final TagSimilarityMeasure similarityMeasure, String outputFile, int threads) throws IOException{
		final LinkedList<String> tags = tagsList;
		FileWriter fWriter = null;
		try {
			fWriter = new FileWriter(outputFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		final DecimalFormat formatter = new DecimalFormat("0.00000000000000000");
		formatter.setRoundingMode(RoundingMode.HALF_UP);
								
		final FileWriter writer = fWriter;
		ParallelForEach.loop(tags,
				threads,
				new Procedure<String>() {
                    @Override
                    public void call(String comparingTag) throws Exception {
                    	int start = tags.indexOf(comparingTag);
                    	for(String comparedTag : tags.subList(start+1, tags.size() )){
            				double cc = similarityMeasure.calculateSimilarity(comparingTag, comparedTag);
//            				if(!(cc < 0.001 && cc > -.001)){
            					// Remove newlines, commas and apostrophes that may distort the CSV file when being written.
            					synchronized(writer){
            					writer.append("\"" + comparingTag.replace("\"", "").replace("\n", "").replace(",", "") + '"'+ ',' + '"' + comparedTag.replace("\"", "").replace("\n", "").replace(",", "") + '"' + "," + formatter.format(cc) +"\n");
            					}
//    IOException        				}
            				
            			}
                    }
                }
				);
		
		

		fWriter.flush();
	    fWriter.close();
		
	}
	
	public static void generateTagSimilarityCSV(LinkedList<String> tagsList, final TagSimilarityMeasure similarityMeasure, String outputFile) throws IOException{
		generateTagSimilarityCSV(tagsList, similarityMeasure, outputFile, Runtime.getRuntime().availableProcessors());
	}
	
	public static void exponentialFileSplit(String inputFile){
		List<String> lines = null;
		try {
			lines = Files.readAllLines(Paths.get(inputFile), Charset.defaultCharset());
		} catch (IOException e) {
			e.printStackTrace();
		}
		int totalLines = lines.size();
		
		double base = solveQuartic((double) totalLines*-1)[1];
		
		for(int i = 0; i<5; i++){
			int start = (int) Math.pow(base, i);
			int end = (int) Math.pow(base, i+1) > lines.size() ? lines.size() : (int) Math.pow(base, i+1);
			FileWriter fWriter = null;
			try {
				fWriter = new FileWriter(new File(inputFile.replace(".csv", "")+"-"+i+".csv" ));
				for(String line : lines.subList(start, end)){
					fWriter.write(line+"\n");
				}
				fWriter.flush();
				fWriter.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
	}
	
	public static void generateMostFrequentResources(String bibsonomyDSdir, String outputDir){
		FileInputStream fileStream;
		BufferedInputStream bufferedStream;
		BufferedReader readerStream;
		
		List<BibsonomyRecord> overlappingTagEntries = new LinkedList<BibsonomyRecord>();
		
		try {
			fileStream = new FileInputStream(bibsonomyDSdir);
			bufferedStream = new BufferedInputStream(fileStream);
			readerStream = new BufferedReader(new InputStreamReader(bufferedStream));
			
			while(readerStream.ready()){
				String line = readerStream.readLine();
				String tagInfo[] = line.split("\t");
				if( tagInfo.length == 5 && (
						!WS4J.findDefinitions(tagInfo[1], POS.a).isEmpty() ||
						!WS4J.findDefinitions(tagInfo[1], POS.n).isEmpty() ||
						!WS4J.findDefinitions(tagInfo[1], POS.r).isEmpty() ||
						!WS4J.findDefinitions(tagInfo[1], POS.v).isEmpty()
						)){
					overlappingTagEntries.add(new BibsonomyRecord(tagInfo[2], line));
				}
			}
						
			readerStream.close();
			fileStream.close();
			bufferedStream.close();
			
		} catch (FileNotFoundException e) {
			System.out.println("File not found exception: "+e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("IOException: "+e.getMessage());
			e.printStackTrace();
		}
		
		Collections.sort(overlappingTagEntries);
		
		List<EntryFrequency> bibsonomyEntries  = new LinkedList<EntryFrequency>();
		
		String lastContentID = overlappingTagEntries.get(0).contentID;
		int frequency = 1;
		
		for(BibsonomyRecord rec : overlappingTagEntries.subList(1, overlappingTagEntries.size())){
			if(lastContentID.equals(rec.contentID)){
				if(rec.equals(overlappingTagEntries.get(overlappingTagEntries.size()-1)))
					bibsonomyEntries.add(new EntryFrequency(lastContentID, frequency));
				else
					frequency++;
			}else{
				bibsonomyEntries.add(new EntryFrequency(lastContentID, frequency));
				lastContentID = rec.contentID;
				frequency = 1;
			}
		}
		
		Collections.sort(bibsonomyEntries);
		Collections.reverse(bibsonomyEntries);
						
		ArrayList<String> mostCommonResources= new ArrayList<String>();
		for(EntryFrequency bib : bibsonomyEntries.subList(0, 2000)){
			mostCommonResources.add(bib.contentID);
		}
		
		FileWriter writer = null;
		
		try {
			writer = new FileWriter(outputDir);
			for(BibsonomyRecord rec : overlappingTagEntries){
				if(mostCommonResources.contains(rec.contentID)){
					writer.write(rec.line+"\n");
				}	
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			System.out.println("IOException: "+e.getMessage());
		}
        
	}
	
	 /**
     * Solve a quartic equation of the form ax^4+bx^3+cx^2+cx^1+d=0. The roots
     * are returned in a sorted array of doubles in increasing order.
     * Code taken from: https://github.com/fpsunflower/sunflow/blob/master/src/org/sunflow/math/Solvers.java
     * @param e coefficient of x^0
     * @return a sorted array of roots, or <code>null</code> if no solutions
     *         exist
     */
    private static double[] solveQuartic(double e) {
        double p = -0.375 * 1 + 1;
        double q = 0.125 * 1 * 1 - 0.5 * 1 * 1 + 1;
        double r = -0.01171875 * 1 * 1 + 0.0625 * 1 * 1 - 0.25 * 1 * 1 + e;
        double z = solveCubicForQuartic(-0.5 * p, -r, 0.5 * r * p - 0.125 * q * q);
        double d1 = 2.0 * z - p;
        if (d1 < 0) {
            if (d1 > 1.0e-10)
                d1 = 0;
            else
                return null;
        }
        double d2;
        if (d1 < 1.0e-10) {
            d2 = z * z - r;
            if (d2 < 0)
                return null;
            d2 = Math.sqrt(d2);
        } else {
            d1 = Math.sqrt(d1);
            d2 = 0.5 * q / d1;
        }
        double q1 = d1 * d1;
        double q2 = -0.25 * 1;
        double pm = q1 - 4 * (z - d2);
        double pp = q1 - 4 * (z + d2);
        if (pm >= 0 && pp >= 0) {
            pm = Math.sqrt(pm);
            pp = Math.sqrt(pp);
            double[] results = new double[4];
            results[0] = -0.5 * (d1 + pm) + q2;
            results[1] = -0.5 * (d1 - pm) + q2;
            results[2] = 0.5 * (d1 + pp) + q2;
            results[3] = 0.5 * (d1 - pp) + q2;
            // tiny insertion sort
            for (int i = 1; i < 4; i++) {
                for (int j = i; j > 0 && results[j - 1] > results[j]; j--) {
                    double t = results[j];
                    results[j] = results[j - 1];
                    results[j - 1] = t;
                }
            }
            return results;
        } else if (pm >= 0) {
            pm = Math.sqrt(pm);
            double[] results = new double[2];
            results[0] = -0.5 * (d1 + pm) + q2;
            results[1] = -0.5 * (d1 - pm) + q2;
            return results;
        } else if (pp >= 0) {
            pp = Math.sqrt(pp);
            double[] results = new double[2];
            results[0] = 0.5 * (d1 - pp) + q2;
            results[1] = 0.5 * (d1 + pp) + q2;
            return results;
        }
        return null;
    }
    private static double solveCubicForQuartic(double p, double q, double r) {
        double A2 = p * p;
        double Q = (A2 - 3.0 * q) / 9.0;
        double R = (p * (A2 - 4.5 * q) + 13.5 * r) / 27.0;
        double Q3 = Q * Q * Q;
        double R2 = R * R;
        double d = Q3 - R2;
        double an = p / 3.0;
        if (d >= 0) {
            d = R / Math.sqrt(Q3);
            double theta = Math.acos(d) / 3.0;
            double sQ = -2.0 * Math.sqrt(Q);
            return sQ * Math.cos(theta) - an;
        } else {
            double sQ = Math.pow(Math.sqrt(R2 - Q3) + Math.abs(R), 1.0 / 3.0);
            if (R < 0)
                return (sQ + Q / sQ) - an;
            else
                return -(sQ + Q / sQ) - an;
        }
    }

}