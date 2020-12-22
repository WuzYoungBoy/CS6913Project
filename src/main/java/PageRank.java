
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;

import java.io.*;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class PageRank {

    private static  LinkedHashMap <String, Integer> getAllUrls(String sourcePath) {
        System.out.println("start loading urls....");
        LinkedHashMap <String, Integer> urls = new LinkedHashMap<>();
        try {
            BufferedReader urlBufferedReader = new BufferedReader(new FileReader(sourcePath));
            String line;
            int docNum = 0;

            while ((line = urlBufferedReader.readLine()) != null) {
                if(docNum >= 100000) {break;}
                if (line.equals("<TEXT>")) {
                    urls.put(urlBufferedReader.readLine(), docNum++);
                }
            }
            urlBufferedReader.close();
            System.out.println("All urls loaded.");
        }catch (IOException ioe) { ioe.printStackTrace(); }
        return urls;
    }

    private static String doubleToString(Double d) {
        if (d == null)
            return null;
        if (d.isNaN() || d.isInfinite())
            return d.toString();

        if (d.doubleValue() == 0)
            return "0";
        return new BigDecimal(d.toString()).stripTrailingZeros().toPlainString();
    }

    private static boolean checkForConvergence(ArrayList<Double> oldValues, ArrayList<Double> newValues, double threshold) {

        boolean hasConverged = true;
        for(int i = 0; i < oldValues.size(); i++) {
            if (Math.abs(oldValues.get(i) - newValues.get(i)) > threshold) {
                hasConverged = false;
            }
        }
        return hasConverged;
    }


    private static void GeneratePageRankValue(String sourceFilePath, String outputPath) {
        System.out.println("Generating PageRank Values...");
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(sourceFilePath));
            BufferedWriter pageRankValueBufferedWriter = new BufferedWriter(new FileWriter(outputPath.concat("pageRankValues")));

            LinkedHashMap <String, Integer> urls = getAllUrls(sourceFilePath);
            int urlNum = urls.size();
            ArrayList<Double>  pageRankValue = new ArrayList<>(Arrays.asList(new Double[urls.size()]));
            Collections.fill(pageRankValue, 1.0/urls.size());
            ArrayList<Integer> offset = new ArrayList<>();
            ArrayList<Integer> outNeighbors = new ArrayList<>();
            ArrayList <Integer> indexOfNodeWithDegree = new ArrayList<>();

            TreeMap<Integer, HashSet<Integer>> connectivity = new TreeMap<>();

            int docNum = 0, linkPageNum = 0;
            StringBuilder stringBuilder = new StringBuilder();
            String line;

            while ((line = bufferedReader.readLine()) != null ) {

                if(docNum >= 100000) {break;}
                if (line.equals("<TEXT>")) {
                    String URL = bufferedReader.readLine();

                    while (!(line = bufferedReader.readLine()).equals("</TEXT>")) {
                        stringBuilder.append(line);
                        stringBuilder.append(System.lineSeparator());
                    }
                    UrlDetector urlDetector = new UrlDetector(stringBuilder.toString(), UrlDetectorOptions.Default);
                    HashSet <Integer> links = null;
                    try {

                        links  = new HashSet<>(urlDetector.detect().stream().filter(a-> urls.get(a.toString()) != null).map(e -> urls.get(e.toString())).collect(Collectors.toList()));
                        HashSet<Integer> existedLinks = connectivity.get(docNum);
                        if(existedLinks == null) {
                            connectivity.put(docNum, links);
                        }else{
                            existedLinks.addAll(links);
                            connectivity.put(docNum, existedLinks);
                        }

                    } catch (Exception e) {
                        System.out.println("error. skipped.");
                    }

                    stringBuilder.setLength(0);
                    docNum++;
                }
            }

            for(Map.Entry<Integer, HashSet<Integer>> entry: connectivity.entrySet()) {
                List<Integer> linksToDelete = entry.getValue().stream().filter(e->connectivity.get(e) == null).collect(Collectors.toList());
                HashSet<Integer> temp = entry.getValue();
                temp.removeAll(linksToDelete);
                connectivity.put(entry.getKey(), temp);
            }

            int counter = 0;
            boolean hasConverged = false;
            while(!hasConverged) {
                ArrayList<Double>  newPageRankValue = new ArrayList<>(Arrays.asList(new Double[urls.size()]));
                Collections.fill(newPageRankValue, 0.15/urls.size());

                for(Map.Entry<Integer, HashSet<Integer>> entry: connectivity.entrySet()) {
                    for(int j : entry.getValue()) {
                        double prevValue = newPageRankValue.get(j);
                        newPageRankValue.set(j, prevValue + 0.85 * pageRankValue.get(entry.getKey()));
                    }
                }
                hasConverged = checkForConvergence(pageRankValue, newPageRankValue, 0.0001);
                pageRankValue = newPageRankValue;
                counter++;
            }

            System.out.println("number of iteration until convergence: " + counter);

            HashSet <Double> unique = new HashSet<>(pageRankValue);
//            System.out.println(Arrays.toString(unique.toArray()));
//            System.out.println(pageRankValue.size());

            for(double value: pageRankValue) {
                pageRankValueBufferedWriter.write(doubleToString(value));
                pageRankValueBufferedWriter.newLine();
            }
            pageRankValueBufferedWriter.close();

        }catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }


    public static void main(String[] args){

        System.out.println("Usage: add 2 parameters - sourceFilePath outputPath");
        System.out.println("sourceFilePath: The absolute path to the trec file.(fulldocs-new.trec)");
        System.out.println("outputPath: the output folder name or the absolute path of that folder. Must end with path separator.(Linux:/ Window:\\)");
        System.out.println();

        if(args.length != 2) {
            System.out.println("Error: Incorrect parameters. Please try again.");
            System.exit(1);
        }
        if(!args[1].endsWith(File.separator)){
            System.out.println("Error: Output path must end with correct path separator. (Linux:/  Window:\\)");
            System.exit(1);
        }

        String sourceFilePath = args[0];
        String outputPath = args[1];
        GeneratePageRankValue(sourceFilePath, outputPath);
    }
}
