import java.io.File;


public class InvertedIndexConstruction {

    public static void main(String[] args) {
        System.out.println("Usage: add 3 parameters - sourceFilePath outputPath bufferSize");
        System.out.println("sourceFilePath: The absolute path to the trec file.(fulldocs-new.trec)");
        System.out.println("outputPath: the output folder name or the absolute path of that folder. Must end with path separator.(Linux:/ Window:\\)");
        System.out.println("bufferSize: Maximum number of word tuples in the buffer. Please use 20000000 for now.");
        System.out.println();

        if(args.length != 3) {
            System.out.println("Error: Incorrect parameters. Please try again.");
            System.exit(1);
        }
        if(!args[1].endsWith(File.separator)){
            System.out.println("Error: Output path must end with correct path separator. (Linux:/  Window:\\)");
            System.exit(1);
        }

        String sourceFilePath = args[0];
        String outputPath = args[1];
        int bufferSize = Integer.parseInt(args[2]);

        System.out.println(String.format("TREC file in: %s", sourceFilePath));
        System.out.println(String.format("Output to: %s",outputPath));
        System.out.println();


        TrecParser trecParser = new TrecParser(bufferSize);
        long begin = System.currentTimeMillis();
        int fileNum = trecParser.parseAndSortPhase(sourceFilePath,outputPath);
        long end = System.currentTimeMillis();
        System.out.println("Total Parsing Time: "+(end - begin));

        int maximumDegree = fileNum;

        PostingMerger postingMerger = new PostingMerger(maximumDegree);
        long begin1 = System.currentTimeMillis();
        String intermediatePostingPath = postingMerger.start(fileNum, outputPath);
        long end1 = System.currentTimeMillis();
        System.out.println("Total Merging Time: " + (end1 - begin1));
        System.out.println(intermediatePostingPath);

        String intermediatePostingPath2 = "C:\\Users\\MyPC\\Desktop\\Web Search Engine\\HW3temp\\IP1_1";
        BinaryFormatter binaryFormatter = new BinaryFormatter();
        long begin2 = System.currentTimeMillis();
        binaryFormatter.generateInvertedIndex(intermediatePostingPath,outputPath);
        long end2 = System.currentTimeMillis();
        System.out.println("Total Reformatting Time: " + (end2 - begin2));


    }
}
