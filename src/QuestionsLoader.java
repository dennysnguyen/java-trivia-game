import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuestionsLoader {
    public static Map<Integer, List<String>> loadQuestionsFromFile(String filePath) {
        Map<Integer, List<String>> questionsMap = new HashMap<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineCount = 0;
            while ((line = bufferedReader.readLine()) != null) {
                int questionNumber = lineCount / 5 + 1;
                questionsMap.computeIfAbsent(questionNumber, k -> new ArrayList<>()).add(line);
                lineCount++;
            }
        } catch (IOException e) {
            System.out.println("Error reading questions file: " + filePath);
            e.printStackTrace();
        }
        return questionsMap;
    }
}
