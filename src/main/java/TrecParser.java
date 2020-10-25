import org.apache.commons.lang3.StringUtils;
import java.io.*;
import java.util.*;

class TrecParser {
    private int bufferSize;

    TrecParser(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    private void sortInvertedIndex(String[][] invertedIndex, int fromIndex, int toIndex, Comparator<String[]>lexOrder, Comparator<String[]> lenOrder, Comparator<String[]>numOrder) {
        Arrays.parallelSort(invertedIndex, fromIndex, toIndex, lexOrder.thenComparing(lenOrder).thenComparing(numOrder));
    }

    private void updateFreqMap(Map<String, MutableInt> freqMap, String word) {
        MutableInt count = freqMap.get(word);
        if (count == null) freqMap.put(word, new MutableInt());
        else count.increment();
    }

    private void generateInitialIntermediatePostingsASCII(String[][] invertedIndex, int fileCount, int wordCount, String outputFilePath) throws Exception {
        BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outputFilePath.concat("IP".concat(Integer.toString(fileCount).concat("_0"))))));
        for (int i = 0; i < wordCount; i++) {
            bw.write(invertedIndex[i][0]);
            bw.write(" ");
            bw.write(invertedIndex[i][1]);
            bw.write(",");
            bw.write(invertedIndex[i][2]);
            bw.newLine();
            invertedIndex[i] = null;
        }
        bw.close();
    }

    private void writeToIntermediatePageTableASCII(BufferedWriter bwpt, String URL, String docLen) throws IOException {
        bwpt.write(URL); bwpt.write(" "); bwpt.write(docLen); bwpt.newLine();
    }

    int parseAndSortPhase(String sourceFilePath, String outputPath) {
        String[][] invertedIndex = new String[bufferSize][3];
        Map<String, MutableInt> freqMap = new TreeMap<>();
        Comparator<String[]> lexOrder = Comparator.comparing(row -> row[0]);
        Comparator<String[]> lenOrder = Comparator.comparing(row-> row[1].length());
        Comparator<String[]> numOrder = Comparator.comparing(row -> row[1]);
        int fileNum = 0, docNum = 0, wordCount = 0;
        String line;

        try {
            BufferedReader br = new BufferedReader(new FileReader(sourceFilePath));
            BufferedWriter bwpt = new BufferedWriter(new FileWriter(outputPath.concat("PageTable")));

            System.out.println("Parsing docs");
            long begin = System.currentTimeMillis();
            while ((line = br.readLine()) != null) {
                if (line.equals("<TEXT>")) {

                    String docNumStr = String.valueOf(docNum);
                    String URL = br.readLine();
                    int docLen = 0;

                    while (!(line = br.readLine()).equals("</TEXT>")) {

                        String [] words = StringUtils.split(line, " \"?.,:()“”;!~'|#{}[]$‘’*-+%&—–_/");
                        for (String word : words) {
                            if(word.length() > 40) continue;
                            docLen++;
                            updateFreqMap(freqMap, word.toLowerCase());
                        }
                    }
                    for (Map.Entry<String, MutableInt> entry : freqMap.entrySet()) {

                        if (wordCount >= bufferSize) {
                            long end = System.currentTimeMillis();
                            System.out.println("Elapsed Time: "+(end - begin));

                            System.out.println("start sort");
                            long begin0 = System.currentTimeMillis();
                            sortInvertedIndex(invertedIndex, 0, bufferSize,lexOrder, lenOrder, numOrder);
                            long end0 = System.currentTimeMillis();
                            System.out.println(String.format("Sort Time: %d",(end0 - begin0)));

                            System.out.println("generate intermediate postings");
                            long begin1 = System.currentTimeMillis();
                            generateInitialIntermediatePostingsASCII(invertedIndex, ++fileNum, bufferSize, outputPath);
                            long end1 = System.currentTimeMillis();
                            System.out.println(String.format("Write Time: %d",(end1 - begin1)));

                            wordCount = 0;

                            if(fileNum == 256) {
                                return 256;
                            }
                        }
                        invertedIndex[wordCount++] = new String[]{entry.getKey(), docNumStr, String.valueOf(entry.getValue().get())};
                    }
                    docNum++;
                    freqMap.clear();
                    writeToIntermediatePageTableASCII(bwpt, URL, String.valueOf(docLen));
                    if (docNum % 10000 == 0) System.out.println(docNum);
                }
            }
            sortInvertedIndex(invertedIndex, 0, wordCount,lexOrder, lenOrder, numOrder);
            generateInitialIntermediatePostingsASCII(invertedIndex, fileNum, wordCount, outputPath);
            bwpt.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileNum;
    }
}
