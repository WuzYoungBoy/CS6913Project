import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import com.google.common.collect.MinMaxPriorityQueue;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class QueryProcessor {

    private LinkedHashMap<String, long []> lexicon;
    private LinkedHashMap<Integer, Page> pageTable;
    private String invertedIndexPath;
    private String sourceFilePath;
    private double davg;


    public QueryProcessor(String lexiconPath, String pageTablePath, String invertedIndexPath, String sourceFilePath) {
        this.lexicon = loadLexicon(lexiconPath);
        System.out.println("Finish loading lexicon");
        this.pageTable = loadPageTable(pageTablePath);
        System.out.println("Finish loading page table");
        this.invertedIndexPath = invertedIndexPath;
        this.sourceFilePath = sourceFilePath;
    }


    private LinkedHashMap<String, long []> loadLexicon(String lexiconPath) {
        LinkedHashMap<String, long[] > lexicon = new LinkedHashMap<>();
        try {
            BufferedReader lexiconBufferedReader = new BufferedReader(new FileReader(lexiconPath));
            String line;
            String [] lexiconInfo;
            while((line = lexiconBufferedReader.readLine()) != null) {
                lexiconInfo = StringUtils.split(line, ' ');
                lexicon.put(lexiconInfo[0], new long [] {
                        NumberUtils.toLong(lexiconInfo[1]),NumberUtils.toLong(lexiconInfo[2]),NumberUtils.toLong(lexiconInfo[3])
                });
            }
            lexiconBufferedReader.close();
        }catch (IOException ioe) { ioe.printStackTrace(); }
        return lexicon;
    }

    private LinkedHashMap<Integer, Page> loadPageTable(String pageTablePath) {
        LinkedHashMap<Integer, Page> pageTable = new LinkedHashMap<>();
        try {
            BufferedReader PageTableBufferedReader = new BufferedReader(new FileReader(pageTablePath));
            String line;
            String [] pageInfo;
            int docId = 0; int docLen; double totalDocLen = 0;

            while((line = PageTableBufferedReader.readLine()) != null) {
                pageInfo = StringUtils.split(line,' ');
                docLen = NumberUtils.toInt(pageInfo[1]);
                totalDocLen += docLen;
                pageTable.put(docId++, new Page(pageInfo[0], docLen, NumberUtils.toLong(pageInfo[2]), NumberUtils.toLong(pageInfo[3])));
            }
            this.davg = totalDocLen / pageTable.size();
            System.out.println("page table size: " + pageTable.size());
            PageTableBufferedReader.close();
        }catch (IOException ioe) { ioe.printStackTrace(); }
        return pageTable;
    }

    private byte [] readBinaryFile(String filePath, long start, long end) {
        byte [] buffer = new byte[(int)(end - start)];
        try{
            BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(filePath));
            long bytesToSkip = start;
            while(bytesToSkip > 0) {bytesToSkip -= bufferedInputStream.skip(bytesToSkip); }
            bufferedInputStream.read(buffer);
        } catch (IOException ioe) {ioe.printStackTrace();}
        return buffer;
    }

    private ListPointer openList(String word)  {
        long [] lexiconInfo = lexicon.get(word);
        BufferedInputStream invertedIndexBufferedInputStream = null;
        if (lexiconInfo == null) { return null; }
        try {
            invertedIndexBufferedInputStream = new BufferedInputStream(new FileInputStream(invertedIndexPath));
            long bytesToSkip = lexiconInfo[1];
            while(bytesToSkip > 0) {bytesToSkip -= invertedIndexBufferedInputStream.skip(bytesToSkip); }
        }
        catch (IOException ioe) { ioe.printStackTrace();}
        return new ListPointer(word, lexiconInfo[0], invertedIndexBufferedInputStream);
    }

    private void closeList(ListPointer listPointer) {
        try{ listPointer.getInvertedIndexBufferedInputStream().close();}
        catch (IOException ioe) {ioe.printStackTrace();}
    }

    private int nextGEQ(ListPointer listPointer, int threshold) {
        if(threshold > listPointer.getLastDocIds()[listPointer.getLastDocIds().length - 1]) {return pageTable.size();}

        while (listPointer.getLastDocIds()[listPointer.getCurrentBlockIndex()] < threshold) {
            if( listPointer.getCurrentBlockIndex() + 1 >= listPointer.getBlockNum()) { return pageTable.size();}

            long bytesToSkip = 0;
            if (!listPointer.isDocIdBlockUncompressed()) {
                bytesToSkip = listPointer.getDocIdSizes()[listPointer.getCurrentBlockIndex()] + listPointer.getFreqSizes()[listPointer.getCurrentBlockIndex()];
            } else if (!listPointer.isFreqBlockUncompressed()) {
                bytesToSkip = listPointer.getFreqSizes()[listPointer.getCurrentBlockIndex()];
            }

            try {
                while(bytesToSkip > 0) { bytesToSkip -= listPointer.getInvertedIndexBufferedInputStream().skip(bytesToSkip);}
            } catch (IOException e) { e.printStackTrace(); }

            listPointer.setDocIdBlockUncompressed(false);
            listPointer.setFreqBlockUncompressed(false);
            listPointer.setPrevLastDocId(listPointer.getLastDocIds()[listPointer.getCurrentBlockIndex()]);
            listPointer.setCurrentBlockIndex(listPointer.getCurrentBlockIndex() + 1);
        }
        int [] uncompressedDocIdBlock = listPointer.uncompressDocIdBlock();

        while (uncompressedDocIdBlock[listPointer.getCurrentInBlockIndex()] < threshold) {
            listPointer.setCurrentInBlockIndex(listPointer.getCurrentInBlockIndex() + 1);
        }
        return uncompressedDocIdBlock[listPointer.getCurrentInBlockIndex()];
    }

    private int getFreq(ListPointer listPointer) {
        return listPointer.uncompressFreqBlock()[listPointer.getCurrentInBlockIndex()];
    }

    private double K(int d) { return 1.2 * (0.25 + 0.75 *(d/davg)); }

    private double CalculateBM25(ListPointer[] listPointers, int[] frequencies, int d) {
        double score = 0;
        for (int i = 0; i < listPointers.length; i++) {
            int ft = listPointers[i].getOccurrence();
            score += Math.log((pageTable.size() - ft + 0.5)/(ft + 0.5)) * ((2.2 * frequencies[i])/ (K(d) + frequencies[i]));
        }
        return score;
    }

    private MinMaxPriorityQueue <double []> conjunctiveDAATQueryProcessing(ListPointer[] listPointers) {

        MinMaxPriorityQueue <double []> topTenHeap = MinMaxPriorityQueue.
                orderedBy((Comparator<double[]>) (a, b) -> Double.compare(b[1], a[1])).maximumSize(10).create();

        int firstDocId = 0;
        int restDocIds = 0;

        while (firstDocId < pageTable.size()) {
            firstDocId = nextGEQ(listPointers[0],firstDocId);
            for (int i = 1; (i < listPointers.length) && ((restDocIds = nextGEQ(listPointers[i], firstDocId)) == firstDocId); i++);

            if(restDocIds > firstDocId) {firstDocId = restDocIds;}
            else {
                if(restDocIds >= pageTable.size()) {break;}
                int [] frequencies = new int [listPointers.length];
                for (int i = 0; i < listPointers.length; i++) { frequencies[i] = getFreq(listPointers[i]); }
                double BM25 = CalculateBM25(listPointers,frequencies, pageTable.get(restDocIds).getTermNum());
                topTenHeap.add(new double[]{firstDocId, BM25});
                firstDocId++;
            }
        }

        for (ListPointer listPointer : listPointers) { closeList(listPointer); }
        return topTenHeap;
    }

    private MinMaxPriorityQueue <double []> disjunctiveDAATQueryProcessing(ListPointer[] listPointers) {

        MinMaxPriorityQueue <int []> listPointerHeap = MinMaxPriorityQueue.
                orderedBy((Comparator<int []>) (o1, o2) -> Integer.compare(o1[1],o2[1])).maximumSize(listPointers.length).create();
        MinMaxPriorityQueue <double []> topTenHeap = MinMaxPriorityQueue.
                orderedBy((Comparator<double[]>) (a, b) -> Double.compare(b[1], a[1])).maximumSize(10).create();

        for (int i = 0; i < listPointers.length; i++) {
            listPointerHeap.add(new int [] {i, nextGEQ(listPointers[i], 0)});
        }

        ArrayList<Integer> poppedListedPointersIndex = new ArrayList<>();

        while(listPointerHeap.size() > 0 && listPointerHeap.peek()[1] < pageTable.size()) {
            int [] minListPointerInfo = listPointerHeap.poll();
            poppedListedPointersIndex.add(minListPointerInfo[0]);
            int minDocId = minListPointerInfo[1];

            while(listPointerHeap.size() > 0 && listPointerHeap.peek()[1] == minDocId) {
                poppedListedPointersIndex.add(listPointerHeap.poll()[0]);
            }

            int [] frequencies = new int [poppedListedPointersIndex.size()];
            ListPointer [] minListPointers = new ListPointer [poppedListedPointersIndex.size()];

            for (int i = 0; i < poppedListedPointersIndex.size(); i++) {
                minListPointers[i] = listPointers[poppedListedPointersIndex.get(i)];
                frequencies[i] = getFreq(listPointers[poppedListedPointersIndex.get(i)]);
            }
            double BM25 = CalculateBM25(minListPointers,frequencies, pageTable.get(minDocId).getTermNum());
            topTenHeap.add(new double[]{minDocId, BM25});

            for(int i = 0; i < poppedListedPointersIndex.size(); i++) {
                listPointerHeap.add(new int [] {i, nextGEQ(listPointers[poppedListedPointersIndex.get(i)],minDocId + 1)});
            }
            poppedListedPointersIndex.clear();
        }

        for (ListPointer listPointer : listPointers) { closeList(listPointer); }
        return topTenHeap;
    }

    private void GenerateSnippets(MinMaxPriorityQueue<double[]> topTenHeap, String[] words) {
        double [] resultPageInfo;
        while ((resultPageInfo = topTenHeap.poll()) != null) {
            Page page = pageTable.get((int)resultPageInfo[0]);
            ArrayList <Integer> positions =  new ArrayList<>();
            HashSet<String> foundWords = new HashSet<>();
            System.out.println("URL: ".concat(page.getURL()));
            System.out.println("BM25 Score: ".concat("" + resultPageInfo[1]));

            int indexOfLine = 0;
            String document = new String(readBinaryFile(sourceFilePath, page.getStartPosition(), page.getEndPosition()), StandardCharsets.UTF_8);
            for (int i = 0; i < 5; i++) {indexOfLine = document.indexOf('\n', indexOfLine + 1); }
            document = document.substring(indexOfLine);
            String regex = "("+ StringUtils.join(words,")|(") + ")";
            Matcher matcher = Pattern.compile(regex).matcher(document.toLowerCase());

            while(matcher.find()) {
                positions.add(matcher.start());
                foundWords.add(matcher.group());
                if(foundWords.size() == words.length) { break; }
            }
            System.out.println("Snippet:");
            int firstPos = positions.get(0);
            int secondPos = words.length == 1? firstPos : positions.get(positions.size() - 1);

            int start = document.lastIndexOf('\n', firstPos);
            if (start == -1) {start = Math.min(firstPos - 50, 0);};
            int end = document.indexOf('\n', secondPos);
            if (end == -1) {end = Math.max(secondPos + 50, document.length());}

            System.out.println(document.substring(start, end));
            System.out.println();
        }
    }

    private void processQuery() {
        Scanner userInput = new Scanner(System.in);
        System.out.println("Please enter your query: ");
        while(userInput.hasNextLine()){
            String query = userInput.nextLine();
            String [] words = StringUtils.split(query, ' ');
            if(words.length > 10) { System.out.println("Please reduce query size to less than 10 words and try again.");continue;}

            boolean isConjunctive = true;
            System.out.println("Please choose query mode. Enter C for conjunctive or D for disjunctive.");
            while(userInput.hasNextLine()) {
                String mode = userInput.nextLine();
                if(mode.toLowerCase().equals("c")) {
                    isConjunctive = true; break;}
                if(mode.toLowerCase().equals("d")) {
                    isConjunctive = false; break;}
                System.out.println("Please choose query mode. Enter C for conjunctive or D for disjunctive.");
            }

            ListPointer [] listPointers = new ListPointer[words.length];
            for (int i = 0; i < words.length; i++) {
                listPointers[i] = openList(words[i].toLowerCase());
            }

            if (Arrays.stream(listPointers).allMatch(Objects::isNull)) {
                System.out.println("No result Found. Please enter other query.");
                continue;
            }
            if(isConjunctive && Arrays.stream(listPointers).anyMatch(Objects::isNull)) {
                System.out.println("Some words are missing. Enter query again and try disjunctive mode.");
                continue;
            }

            System.out.println("Searching for best result..."); System.out.println();
            if(isConjunctive){
                System.out.println("Conjunctive query processing...");
                System.out.println();
                GenerateSnippets(conjunctiveDAATQueryProcessing(listPointers), words);
            } else {
                System.out.println("Disjunctive query processing...");
                System.out.println();
                ListPointer [] existingListPointers = Arrays.stream(listPointers).filter(Objects::nonNull).toArray(ListPointer[]::new);
                GenerateSnippets(disjunctiveDAATQueryProcessing(existingListPointers),
                        Arrays.stream(existingListPointers).map(ListPointer::getWord).toArray(String[]::new));
            }
            System.out.println();
            System.out.println("Please enter your query: ");
        }
    }

    public static void main(String[] args) {

        if(args.length != 4) {
            System.out.println("Error: Incorrect parameters. Please try again.");
            System.exit(1);
        }

        String lexiconPath = args[0];
        String pageTablePath = args[1];
        String invertedIndexPath = args[2];
        String sourceFilePath = args[3];

        long begin = System.currentTimeMillis();
        QueryProcessor queryProcessor = new QueryProcessor(lexiconPath,pageTablePath,invertedIndexPath,sourceFilePath);
        long end = System.currentTimeMillis();
        System.out.println("Total Loading Time: " + (end - begin));
        System.out.println();

        queryProcessor.processQuery();
    }
}


