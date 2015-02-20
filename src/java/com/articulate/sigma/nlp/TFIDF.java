package com.articulate.sigma.nlp;

/** This code is copyright Articulate Software (c) 2014.   This software is
released under the GNU Public License <http://www.gnu.org/copyleft/gpl.html>.  
Users of this code also consent, by use of this code, to credit Articulate 
Software in any writings, briefings, publications, presentations, or other 
representations of any software which incorporates, builds on, or uses this code.  

This is a simple ChatBot written to illustrate TF/IDF-based information
retrieval.  It searches for the best match between user input and
a dialog corpus.  Once a match is found, it returns the next turn in the
dialog.  If there are multiple, equally good matches, a random one is
chosen.  It was written to use the Cornell Movie Dialogs Corpus
http://www.mpi-sws.org/~cristian/Cornell_Movie-Dialogs_Corpus.html
http://www.mpi-sws.org/~cristian/data/cornell_movie_dialogs_corpus.zip
(Danescu-Niculescu-Mizil and Lee, 2011) and the Open Mind Common Sense 
corpus (Singh et al., 2002) http://www.ontologyportal.org/content/omcsraw.txt.bz2

Author: Adam Pease apease@articulatesoftware.com
*/

/*******************************************************************/

import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.json.simple.*;
import org.json.simple.parser.*;
import com.google.common.collect.ImmutableList;

public class TFIDF {

      // inverse document frequency = log of number of documents divided by 
      // number of documents in which a term appears
    private HashMap<String,Float> idf = new HashMap<String,Float>();

      // number of documents in which a term appears
    private HashMap<String,Integer> docfreq = new HashMap<String,Integer>();

      // the length of a vector composed from each term frequency
    private HashMap<Integer,Float> euclid = new HashMap<Integer,Float>();

      // number of times a term appears in a document
    private HashMap<Integer,HashMap<String,Integer>> tf = new HashMap<Integer,HashMap<String,Integer>>();

      // number of times a term appears in a document * idf
    private HashMap<Integer,HashMap<String,Float>> tfidf = new HashMap<Integer,HashMap<String,Float>>();

    /** English "stop words" such as "a", "at", "them", which have no or little
     * inherent meaning when taken alone. */
    private ArrayList<String> stopwords = new ArrayList<String>();

      // each line of a corpus
    private ArrayList<String> lines = new ArrayList<String>();
    
      // when true, indicates that responses should be the line after the matched line
    private boolean alternating = false;

      // similarity of each document to the query (index -1)
    private HashMap<Integer,Float> docSim = new HashMap<Integer,Float>();

    private Random rand = new Random(); 
    
    /** ***************************************************************
     * @param s An input Object, expected to be a String.
     * @return true if s == null or s is an empty String, else false.
     */
    private static boolean emptyString(Object s) {

        return ((s == null)
                || ((s instanceof String)
                    && s.equals("")));
    }

    /** ***************************************************************
     * Remove punctuation and contractions from a sentence.
     */
    private String removePunctuation(String sentence) {

        Matcher m = null;
        if (emptyString(sentence))
            return sentence;
        m = Pattern.compile("(\\w)\\'re").matcher(sentence);
        while (m.find()) {
            //System.out.println("matches");
            String group = m.group(1);
            sentence = m.replaceFirst(group).toString();
            m.reset(sentence);
        }
        m = Pattern.compile("(\\w)\\'m").matcher(sentence);
        while (m.find()) {
            //System.out.println("matches");
            String group = m.group(1);
            sentence = m.replaceFirst(group).toString();
            m.reset(sentence);
        }
        m = Pattern.compile("(\\w)n\\'t").matcher(sentence);
        while (m.find()) {
            //System.out.println("matches");
            String group = m.group(1);
            sentence = m.replaceFirst(group).toString();
            m.reset(sentence);
        }
        m = Pattern.compile("(\\w)\\'ll").matcher(sentence);
        while (m.find()) {
            //System.out.println("matches");
            String group = m.group(1);
            sentence = m.replaceFirst(group).toString();
            m.reset(sentence);
        }
        m = Pattern.compile("(\\w)\\'s").matcher(sentence);
        while (m.find()) {
            //System.out.println("matches");
            String group = m.group(1);
            sentence = m.replaceFirst(group).toString();
            m.reset(sentence);
        }
        m = Pattern.compile("(\\w)\\'d").matcher(sentence);
        while (m.find()) {
            //System.out.println("matches");
            String group = m.group(1);
            sentence = m.replaceFirst(group).toString();
            m.reset(sentence);
        }
        m = Pattern.compile("(\\w)\\'ve").matcher(sentence);
        while (m.find()) {
            //System.out.println("matches");
            String group = m.group(1);
            sentence = m.replaceFirst(group).toString();
            m.reset(sentence);
        }
        sentence = sentence.replaceAll("\\'","");
        sentence = sentence.replaceAll("\"","");
        sentence = sentence.replaceAll("\\.","");
        sentence = sentence.replaceAll("\\;","");
        sentence = sentence.replaceAll("\\:","");
        sentence = sentence.replaceAll("\\?","");
        sentence = sentence.replaceAll("\\!","");
        sentence = sentence.replaceAll("\\, "," ");
        sentence = sentence.replaceAll("\\,[^ ]",", ");
        sentence = sentence.replaceAll("  "," ");
        return sentence;
    }

    /** ***************************************************************
     * Remove stop words from a sentence.
     */
    private String removeStopWords(String sentence) {

        if (emptyString(sentence))
            return "";
        String result = "";
        ArrayList al = splitToArrayList(sentence);
        if (al == null)
            return "";
        for (int i = 0; i < al.size(); i++) {
            String word = (String) al.get(i);
            if (!stopwords.contains(word.toLowerCase())) {
                if (result == "")
                    result = word;
                else
                    result = result + " " + word;
            }
        }
        return result;
    }

    /** ***************************************************************
     * Check whether the word is a stop word
     */
    private boolean isStopWord(String word) {

        if (emptyString(word))
            return false;
        if (stopwords.contains(word.trim().toLowerCase())) 
            return true;
        return false;
    }

   /** ***************************************************************
     * Read a file of stopwords
     */
    private void readStopWords() {

       // System.out.println("INFO in readStopWords(): Reading stop words");
        File swFile = null;
        String filename = "";
        try {
            swFile = new File("stopwords.txt");
            if (swFile == null) {
                System.out.println("Error in readStopWords(): The stopwords file does not exist in " + filename);
                return;
            }
            filename = swFile.getCanonicalPath();
            FileReader r = new FileReader(swFile);
            LineNumberReader lr = new LineNumberReader(r);
            String line;
            while ((line = lr.readLine()) != null)
                stopwords.add(line.intern());
        }
        catch (Exception i) {
            System.out.println("Error in readStopWords() reading file "
                    + filename + ": " + i.getMessage());
            i.printStackTrace();
        }
        return;
    }

    /** ***************************************************************
     * Return an ArrayList of the string split by spaces.
     */
    private static ArrayList<String> splitToArrayList(String st) {

        if (emptyString(st)) {
            System.out.println("Error in WordNet.splitToArrayList(): empty string input");
            return null;
        }
        String[] sentar = st.split(" ");
        ArrayList<String> words = new ArrayList(Arrays.asList(sentar));
        for (int i = 0; i < words.size(); i++) {
        	if (words.get(i).equals("") || words.get(i) == null || words.get(i).matches("\\s*"))
        		words.remove(i);
        }
        return words;
    }

    /** ***************************************************************
      * inverse document frequency = log of number of documents divided by 
      * number of documents in which a term appears.
      * Note that if the query is included as index -1 then it will
      * get processed too.
      * HashMap<String,Float> idf = new HashMap<String,Float>();
     */
    private void calcIDF(int docCount) {

        Iterator<String> it = docfreq.keySet().iterator();
        while (it.hasNext()) {
            String token = it.next();
            float f = (float) Math.log10(docCount / docfreq.get(token));
            idf.put(token,new Float(f));
        }
        //System.out.println("IDF:\n" + idf);
    }

    /** ***************************************************************
      * HashMap<Integer,HashMap<String,Integer>> tf 
      * HashMap<String,Float> idf 
      * HashMap<Integer,HashMap<String,Float>> tfidf 
      * Note that if the query is included as index -1 then it will
      * get processed too.
     */
    private void calcOneTFIDF(Integer int1) {

        HashMap<String,Integer> tftermlist = tf.get(int1);
        if (tftermlist == null) {
            System.out.println("Error in calcOneTFIDF(): bad index: " + int1);
            return;
        }
        HashMap<String,Float> tfidflist = new HashMap<String,Float>();
        float euc = 0;
        Iterator<String> it2 = tftermlist.keySet().iterator();
        while (it2.hasNext()) {
            String term = it2.next();
            int tfint = tftermlist.get(term).intValue();
            float idffloat = idf.get(term).floatValue();
            float tfidffloat = idffloat * tfint;
            tfidflist.put(term,new Float(tfidffloat));
            euc = euc + (tfidffloat * tfidffloat);
        }
        euclid.put(int1,new Float((float) Math.sqrt(euc)));
        tfidf.put(int1,tfidflist);        
        //System.out.println("TF/IDF:\n" + tfidf);
    } 

    /** ***************************************************************
      * HashMap<Integer,HashMap<String,Integer>> tf 
      * HashMap<String,Float> idf 
      * HashMap<Integer,HashMap<String,Float>> tfidf 
      * Note that if the query is included as index -1 then it will
      * get processed too.
     */
    private void calcTFIDF() {

        Iterator<Integer> it1 = tf.keySet().iterator();
        while (it1.hasNext()) {
            Integer int1 = it1.next();
            calcOneTFIDF(int1);
        }
        //System.out.println("TF/IDF:\n" + tfidf);
    }

    /** ***************************************************************
     * sets the values in tf (term frequency) and tdocfreq (count of
     * documents in which a term appears)
     *
     * @param intlineCount is -1 for query
     */
    private void processDoc(String doc, Integer intlineCount) {

        if (emptyString(doc)) return;
        String line = removePunctuation(doc);
        line = removeStopWords(line);    
        if (emptyString(line.trim())) return;        
        ArrayList<String> tokens = splitToArrayList(line.trim());
        //System.out.println("ProcessDoc: " + tokens);
        HashSet<String> tokensNoDup = new HashSet<String>();
        HashMap<String,Integer> tdocfreq = new HashMap<String,Integer>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            Integer tcount = new Integer(0);
            if (tdocfreq.containsKey(token))
                tcount = tdocfreq.get(token);
            int tcountint = tcount.intValue() + 1;
            tcount = new Integer(tcountint);
            tdocfreq.put(token,tcount);
            if (!docfreq.containsKey(token))
                docfreq.put(token,new Integer(1));
            else {
                if (!tokensNoDup.contains(token)) {
                    Integer intval = docfreq.get(token);
                    int intvalint = intval.intValue();
                    docfreq.put(token,new Integer(intvalint++));
                    tokensNoDup.add(token);
                }
            }    
        }
        //System.out.println("ProcessDoc: adding for doc " + intlineCount + "\n" + tdocfreq);
        tf.put(intlineCount,tdocfreq);
    }

   /** ***************************************************************
     */
    private int readFile(String fname) {

        int linecount = lines.size() - 1;
        int counter = 0;
        String line = "";
        BufferedReader omcs = null;
        try { 
            File file = new File(fname);
            String filename = file.getCanonicalPath();
            omcs = new BufferedReader(new FileReader(filename));
            /* readLine is a bit quirky :
             * it returns the content of a line MINUS the newline.
             * it returns null only for the END of the stream.
             * it returns an empty String if two newlines appear in a row. */
            while ((line = omcs.readLine()) != null) {
                counter++;
                if (counter == 1000) {
                    counter = 0;
                    System.out.print(".");
                }
                if (!emptyString(line)) {
                    linecount++;
                    Integer intlineCount = new Integer(linecount);
                    lines.add(line); 
                    //System.out.println(line);
                    processDoc(line,intlineCount);
                }
            }  
            System.out.println();
            omcs.close();         
        }
        catch (Exception ex)  {
            System.out.println("Error in readFile(): " + ex.getMessage());
            System.out.println("Error in at line: " + line);
            ex.printStackTrace();
        }
        //System.out.println("Movie lines:\n" + lines);
        //System.out.println("TF:\n" + tf);
        return linecount;      
    }

    /** ***************************************************************
     * Assume that query is file index -1
     */
    private void calcDocSim() {

        Integer negone = new Integer(-1);
        HashMap<String,Float> tfidflist = tfidf.get(negone);
        HashMap<String,Float> normquery = new HashMap<String,Float>();
        float euc = euclid.get(negone);
        Iterator<String> it2 = tfidflist.keySet().iterator();
        while (it2.hasNext()) {
           String term = it2.next();
           float tfidffloat = tfidflist.get(term).floatValue();
           normquery.put(term,new Float(tfidffloat / euc));
        }

        Iterator<Integer> it1 = tf.keySet().iterator();
        while (it1.hasNext()) {
            Integer int1 = it1.next();
            if (int1.intValue() != -1) {
                tfidflist = tfidf.get(int1);
                euc = euclid.get(int1);
                float fval = 0;
                Iterator<String> it3 = tfidflist.keySet().iterator();
                while (it3.hasNext()) {
                    String term = it3.next();
                    float tfidffloat = tfidflist.get(term).floatValue();
                    float query = 0;
                    if (normquery.containsKey(term))
                        query = normquery.get(term).floatValue();
                    float normalize = 0;
                    if (euc != 0)
                        normalize = tfidffloat / euc;
                    fval = fval + (normalize * query);
                }
                docSim.put(int1,fval);
            }
        }
        //System.out.println("Doc sim:\n" + docSim);
    }

    /** *************************************************************
     */
    private String matchInput(String input) {
    	
        if (emptyString(input)) 
            System.exit(0);            
        Integer negone = new Integer(-1);
        processDoc(input,negone);
        calcIDF(lines.size()+1);
        calcOneTFIDF(negone);
        //System.out.println("Caclulate docsim");
        calcDocSim();
        //System.out.println("Caclulate sorted sim");
        TreeMap<Float,ArrayList<Integer>> sortedSim = new TreeMap<Float,ArrayList<Integer>>();
          // private HashMap<Integer,Float> docSim = HashMap<Integer,Float>(); 
        Iterator<Integer> it = docSim.keySet().iterator();
        while (it.hasNext()) {
           Integer i = it.next();
           Float f = docSim.get(i);
           if (sortedSim.containsKey(f)) {
               ArrayList<Integer> vals = sortedSim.get(f);
               vals.add(i);
           }
           else { 
               ArrayList<Integer> vals = new ArrayList<Integer>();
               vals.add(i);
               sortedSim.put(f,vals);
           }
        }
        //System.out.println("result: " + sortedSim);
        ArrayList<Integer> vals = sortedSim.get(sortedSim.lastKey());

        int counter = 0;
        int random = 0;
        Integer index = null;
        //do {
            random = rand.nextInt(vals.size());
            index = vals.get(new Integer(random));
            counter++;
        //} while (counter < 50 && ((question && (index.intValue() < cb.omcsLineStart)) || 
        //       (!question && (index.intValue() > cb.omcsLineStart))));
        //System.out.println("query: " + input);
        //System.out.println("line: " + lines.get(index));
        if (!alternating)
            return lines.get(new Integer(index.intValue()));
        else
            return lines.get(new Integer(index.intValue()+1));
    }
    
    /** *************************************************************
     */
    private static void run() {

        TFIDF cb = new TFIDF();
        cb.readStopWords();
        //System.out.println("Read movie lines");
        //int linecount = cb.readMovieLinesFile();
        //System.out.println("Read open mind");
        //linecount = linecount + cb.readOpenMind();
        System.out.println("Read Shell");
        int linecount = cb.readFile("ShellDoc.txt");

        System.out.println("Caclulate IDF");
        cb.calcIDF(linecount);
        System.out.println("Caclulate TFIDF");
        cb.calcTFIDF();

        System.out.println("Hi, I'm a chatbot, tell/ask me something");
        boolean done = false;
        while (!done) {
            Console c = System.console();
            if (c == null) {
                System.err.println("No console.");
                System.exit(1);
            }
            String input = c.readLine("> ");
            //boolean question = input.trim().endsWith("?");
            System.out.println(cb.matchInput(input));
        }
    }

    /** ***************************************************************
     */
    public static Collection<Object[]> prepare() {

    	ArrayList<Object[]> result = new ArrayList<Object[]>();
    	File jsonTestFile = new File("test.json");
    	//System.out.println("INFO in TFIDF.prepare(): reading: " + jsonTestFile);
    	// FIXME ? Maybe we should verify the file exists?
    	String filename = jsonTestFile.getAbsolutePath();
    	JSONParser parser = new JSONParser();  
    	try {  
    		Object obj = parser.parse(new FileReader(filename));  
    		JSONArray jsonObject = (JSONArray) obj; 
    		ListIterator<JSONObject> li = jsonObject.listIterator();
    		while (li.hasNext()) {
    			JSONObject jo = li.next();
    			String fname = (String) jo.get("file");
    			String query = (String) jo.get("query");
    			String answer = (String) jo.get("answer");
            	System.out.println("INFO in TFIDF.prepare(): " + fname + " " + query + " " + answer);
    			result.add(new Object[]{fname,query,answer});
    		}			 
    	} 
    	catch (FileNotFoundException e) {  
    		e.printStackTrace();  
    	} 
    	catch (IOException e) {  
    		e.printStackTrace();  
    	} 
    	catch (ParseException e) {  
    		e.printStackTrace();  
    	} 	
    	catch (Exception e) {  
    		e.printStackTrace();      		
    	} 	
    	//System.out.println(result);
    	return result;    
    }

    /** *************************************************************
     */
    private static void test() {

        HashMap<String,TFIDF> files = new HashMap<String,TFIDF>();
        Collection<Object[]> tests = prepare();
        Random rand = new Random(); 

        for (Object[] test : tests) {
        	String fname = (String) test[0];
        	String query = (String) test[1];
        	String answer = (String) test[2];
        	System.out.print(query + "\t");
            TFIDF cb = new TFIDF();
            if (files.containsKey(fname))
            	cb = files.get(fname);
            else {
	            cb.readStopWords();
	            //System.out.println("Read file: " + fname);
	            int linecount = cb.readFile(fname);
	
	            //System.out.println("Caclulate IDF");
	            cb.calcIDF(linecount);
	            //System.out.println("Caclulate TFIDF");
	            cb.calcTFIDF();
            }
            System.out.println(cb.matchInput(query));
        }
    }

    /** *************************************************************
     */
    public static void main(String[] args) {

        //run();
    	test();
    }
}
 