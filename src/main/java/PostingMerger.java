import java.io.*;
import java.util.*;

class PostingMerger {
    private int maximumDegree;

    PostingMerger(int maximumDegree) {
        this.maximumDegree = maximumDegree;
    }

    private HeapLine getHeapLine(BufferedReader[] brs, int fileBufferIndex) throws IOException {
        String line = brs[fileBufferIndex].readLine();
        return line != null ? new HeapLine(line, fileBufferIndex) : null;
    }

    private void writeToMergedFile(BufferedWriter bw, String prevWord, HeapLine hl) throws IOException{
        if (prevWord.equals(hl.getWord())) {
            bw.write(' ');
            bw.write(hl.getDocFreqString());
        } else {
            bw.newLine();
            bw.write(hl.getLine());
        }
    }

    private String mergeIntermediatePostings(BufferedReader[] brs, PriorityQueue<HeapLine> heap, String outputPath, int fileBufferNum, int mergedFileNum,  int pass) throws IOException {
        HeapLine hl;
        String intermediatePostingPath = outputPath.concat("IP").concat(String.valueOf(mergedFileNum))
                .concat("_").concat(String.valueOf(pass + 1));

        BufferedWriter bw = new BufferedWriter(new FileWriter(intermediatePostingPath));
        for (int fileBufferIndex = 0; fileBufferIndex < fileBufferNum; fileBufferIndex++) {
            hl = getHeapLine(brs, fileBufferIndex);
            if (hl != null) heap.add(hl);
        }

        String prevWord = "\n";
        while ((hl = heap.poll()) != null) {
            int fileBufferIndex = hl.getFileBufferIndex();

            writeToMergedFile(bw, prevWord, hl);
            prevWord = hl.getWord();

            HeapLine refill = getHeapLine(brs, fileBufferIndex);
            if (refill == null) continue;
            heap.add(refill);
        }
        bw.close();
        return intermediatePostingPath;
    }

    private void deleteFiles(ArrayList<File> filesToDelete, BufferedReader[] brs, int fileBufferNum) throws IOException{
        for (int i = 0; i < fileBufferNum; i++) {
            brs[i].close();
            filesToDelete.get(i).delete();
        }
        filesToDelete.clear();
    }

    private String mergePhase(int fileNum, String outputPath, int pass) {

        if (fileNum == 1) {System.out.println("No need to merge"); return null; }
        if(maximumDegree < 1){ System.out.println("Incorrect degree detected."); return null;}

        try {
            BufferedReader[] brs = new BufferedReader[maximumDegree];
            ArrayList <File> filesToDelete = new ArrayList<>();

            int mergedFileNum = 0, fileIndex = 1, fileBufferNum = 0;

            String fileNamePrefix = outputPath.concat("IP");
            String fileNameSuffix = "_".concat(String.valueOf(pass));
            String intermediatePostingPath = null;

            Comparator<HeapLine> lexOrder = Comparator.comparing(hl -> hl.getWord());
            Comparator<HeapLine> lenOrder = Comparator.comparing(hl -> hl.getDocID().length());
            Comparator<HeapLine> numOrder = Comparator.comparing(hl -> hl.getDocID());

//            PriorityQueue<HeapLine> heap = new PriorityQueue<>(maximumDegree, (first, second) -> {
//                int compareTo = first.getWord().compareTo(second.getWord());
//                if (compareTo == 0) return first.getDocID().compareTo(second.getDocID());
//                else return compareTo;
//            });

            PriorityQueue<HeapLine> heap = new PriorityQueue<>(maximumDegree, lexOrder.thenComparing(lenOrder).thenComparing(numOrder));

            while (fileIndex <= fileNum) {
                File intermediatePosting = new File(fileNamePrefix.concat(String.valueOf(fileIndex)).concat(fileNameSuffix));
                brs[fileBufferNum++] = new BufferedReader(new FileReader(intermediatePosting));
                filesToDelete.add(intermediatePosting);
                System.out.println(fileIndex);

                if (fileIndex % maximumDegree == 0) {
                    System.out.println("fileBufferNum: " + fileBufferNum);
                    System.out.println("block " + mergedFileNum);
                    System.out.println();
                    intermediatePostingPath = mergeIntermediatePostings(brs, heap, outputPath, fileBufferNum, ++mergedFileNum, pass);
                    deleteFiles(filesToDelete,brs,fileBufferNum);
                    fileBufferNum = 0;
                }
                fileIndex++;
            }
            if (fileBufferNum != 0) {
                intermediatePostingPath = mergeIntermediatePostings(brs, heap, outputPath, fileBufferNum, ++mergedFileNum, pass);
                deleteFiles(filesToDelete,brs,fileBufferNum);
            }
            System.out.println(fileIndex);
            System.out.println("fileBufferNum: " + fileBufferNum);
            System.out.println("block " + mergedFileNum);

            if(mergedFileNum == 1) return intermediatePostingPath;
            else return mergePhase(mergedFileNum, outputPath,pass + 1);

        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    String start(int fileNum, String outputPath ) {
        return mergePhase(fileNum, outputPath,0);
    }


}
