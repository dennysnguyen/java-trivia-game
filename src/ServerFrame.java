import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerFrame extends JFrame {
    private GameServer server;

    // Panels
    private JPanel cardPanel;
    private CardLayout cardLayout;

    // Setup panel
    private JTextArea logArea;
    private JLabel clientCountLabel;
    private JButton startGameButton;

    // Category panel
    private JPanel categoryPanel;

    // Question panel
    private JLabel questionLabel;
    private JButton[] optionButtons = new JButton[4];
    private JLabel timerLabel;
    private JLabel questionCountLabel;
    private JButton nextButton;
    private Timer timer;
    private int timeLeft;
    private boolean questionActive = false;

    // Results panel
    private JTextArea leaderboardArea;

    // Game state
    private List<List<String>> currentQuestions;
    private List<Integer> currentAnswers;
    private int displayedQuestionIdx = 0;

    private Map<String, List<Integer>> categoryAnswers;

    public ServerFrame() {
        setTitle("Trivia Game Server - HOST");
        setSize(1000, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        categoryAnswers = new HashMap<>();
        categoryAnswers.put("Hardware", List.of(2, 0, 3, 2, 0, 1, 2, 2, 1, 2));
        categoryAnswers.put("Math",     List.of(1, 3, 2, 2, 1, 3, 1, 2, 2, 2));
        categoryAnswers.put("Software", List.of(3, 1, 0, 2, 1, 2, 1, 2, 0, 2));
        categoryAnswers.put("Random",   List.of(0, 2, 2, 2, 1, 2, 3, 1, 0, 2));

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        server = new GameServer(this::log, this::updateClientCount, this::onServerAdvanced);

        try {
            server.start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to start server: " + e.getMessage());
        }

        buildSetupPanel();
        buildCategoryPanel();
        buildQuestionPanel();
        buildResultsPanel();

        add(cardPanel);
        cardLayout.show(cardPanel, "SETUP");
    }

    // ---- Panel Builders ----

    private void buildSetupPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(30, 30, 60));

        JLabel title = new JLabel("Trivia Game - Server Host", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 32));
        title.setForeground(Color.WHITE);
        panel.add(title, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(20, 20, 40));
        logArea.setForeground(Color.GREEN);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "Server Log",
                0, 0, new Font("Arial", Font.BOLD, 12), Color.WHITE));
        panel.add(scroll, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        bottomPanel.setBackground(new Color(30, 30, 60));

        clientCountLabel = new JLabel("Clients connected: 0");
        clientCountLabel.setFont(new Font("Arial", Font.BOLD, 16));
        clientCountLabel.setForeground(Color.CYAN);
        bottomPanel.add(clientCountLabel);

        startGameButton = new JButton("Select Category & Start Game");
        startGameButton.setFont(new Font("Arial", Font.BOLD, 18));
        startGameButton.setBackground(new Color(0, 180, 100));
        startGameButton.setForeground(Color.WHITE);
        startGameButton.addActionListener(e -> cardLayout.show(cardPanel, "CATEGORY"));
        bottomPanel.add(startGameButton);

        panel.add(bottomPanel, BorderLayout.SOUTH);
        cardPanel.add(panel, "SETUP");
    }

    private void buildCategoryPanel() {
        categoryPanel = new JPanel(new BorderLayout(10, 10));
        categoryPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        categoryPanel.setBackground(new Color(173, 216, 230));

        JLabel title = new JLabel("Select a Category", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 48));
        categoryPanel.add(title, BorderLayout.NORTH);

        JPanel btnGrid = new JPanel(new GridLayout(2, 2, 20, 20));
        btnGrid.setOpaque(false);

        String[] categories = {"Hardware", "Software", "Math", "Random"};
        String[] imageFiles = {"image_Hardware.png", "image_Software.png", "image_Math.png", "image_Random.png"};

        for (int i = 0; i < categories.length; i++) {
            ImageIcon icon = new ImageIcon("src/" + imageFiles[i]);
            Image img = icon.getImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH);
            JButton btn = new JButton(categories[i], new ImageIcon(img));
            btn.setVerticalTextPosition(SwingConstants.BOTTOM);
            btn.setHorizontalTextPosition(SwingConstants.CENTER);
            btn.setFont(new Font("Arial", Font.BOLD, 26));
            String cat = categories[i];
            btn.addActionListener(e -> startGameWithCategory(cat));
            btnGrid.add(btn);
        }

        categoryPanel.add(btnGrid, BorderLayout.CENTER);
        cardPanel.add(categoryPanel, "CATEGORY");
    }

    private void buildQuestionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(new Color(173, 216, 230));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        questionCountLabel = new JLabel("Question 1/10", SwingConstants.CENTER);
        questionCountLabel.setFont(new Font("Arial", Font.BOLD, 18));
        header.add(questionCountLabel, BorderLayout.WEST);


        timerLabel = new JLabel("10", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 36));
        timerLabel.setForeground(new Color(180, 0, 0));
        header.add(timerLabel, BorderLayout.EAST);

        nextButton = new JButton("Next →");
        nextButton.setFont(new Font("Arial", Font.BOLD, 18));
        nextButton.setBackground(new Color(255, 165, 0));
        nextButton.setForeground(Color.WHITE);
        nextButton.addActionListener(e -> hostSkip());
        header.add(nextButton, BorderLayout.CENTER);

        panel.add(header, BorderLayout.NORTH);

        // Question label
        questionLabel = new JLabel("Question text here", SwingConstants.CENTER);
        questionLabel.setFont(new Font("Arial", Font.BOLD, 22));
        questionLabel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        panel.add(questionLabel, BorderLayout.CENTER);

        // Option buttons
        JPanel btnPanel = new JPanel(new GridLayout(2, 2, 15, 15));
        btnPanel.setOpaque(false);
        Color[] btnColors = {new Color(100, 149, 237), new Color(255, 165, 0),
                new Color(144, 238, 144), new Color(255, 99, 132)};
        for (int i = 0; i < 4; i++) {
            optionButtons[i] = new JButton("Option " + (i + 1));
            optionButtons[i].setFont(new Font("Arial", Font.BOLD, 20));
            optionButtons[i].setBackground(btnColors[i]);
            optionButtons[i].setOpaque(true);
            optionButtons[i].setEnabled(false); // host can't click, just watches
            btnPanel.add(optionButtons[i]);
        }
        panel.add(btnPanel, BorderLayout.SOUTH);

        // Timer
        timer = new Timer(1000, e -> {
            timeLeft--;
            timerLabel.setText(String.valueOf(timeLeft));
            if (timeLeft <= 5) timerLabel.setForeground(Color.RED);
            if (timeLeft <= 0) {
                timer.stop();
                questionActive = false;
                hostAutoAdvance();
            }
        });

        cardPanel.add(panel, "QUESTION");
    }

    private void buildResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(30, 30, 60));

        JLabel title = new JLabel("🏆 Game Over - Leaderboard Top 10", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 28));
        title.setForeground(Color.YELLOW);
        panel.add(title, BorderLayout.NORTH);

        leaderboardArea = new JTextArea();
        leaderboardArea.setEditable(false);
        leaderboardArea.setFont(new Font("Monospaced", Font.PLAIN, 18));
        leaderboardArea.setBackground(new Color(20, 20, 40));
        leaderboardArea.setForeground(Color.WHITE);
        JScrollPane scroll = new JScrollPane(leaderboardArea);
        panel.add(scroll, BorderLayout.CENTER);

        JButton restartBtn = new JButton("Back to Setup");
        restartBtn.setFont(new Font("Arial", Font.BOLD, 18));
        restartBtn.addActionListener(e -> cardLayout.show(cardPanel, "SETUP"));
        JPanel bottom = new JPanel();
        bottom.setBackground(new Color(30, 30, 60));
        bottom.add(restartBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        cardPanel.add(panel, "RESULTS");
    }

    // ---- Game Logic (Host Side) ----

    private void startGameWithCategory(String category) {
        String filePath = "src/questions_" + category + ".txt";
        Map<Integer, List<String>> qMap = QuestionsLoader.loadQuestionsFromFile(filePath);
        List<Integer> answers = categoryAnswers.get(category);

        List<List<String>> questions = new ArrayList<>();
        for (int i = 1; i <= qMap.size(); i++) {
            questions.add(qMap.get(i));
        }

        currentQuestions = questions;
        currentAnswers = answers;
        displayedQuestionIdx = 0;

        server.loadQuestions(questions, answers);
        server.startGame();

        log("Game started with category: " + category);
        showCurrentQuestion();
        cardLayout.show(cardPanel, "QUESTION");
    }

    private void showCurrentQuestion() {
        if (displayedQuestionIdx >= currentQuestions.size()) {
            showResults();
            return;
        }
        List<String> q = currentQuestions.get(displayedQuestionIdx);
        questionCountLabel.setText("Question " + (displayedQuestionIdx + 1) + " / " + currentQuestions.size());
        questionLabel.setText("<html><div style='text-align:center;'>" + q.get(0) + "</div></html>");
        for (int i = 0; i < 4; i++) {
            optionButtons[i].setText(q.get(i + 1));
            optionButtons[i].setBackground(getDefaultColor(i));
        }
        // Highlight correct answer for host
        int correct = currentAnswers.get(displayedQuestionIdx);
        optionButtons[correct].setBackground(new Color(0, 200, 100));

        timeLeft = 10;
        timerLabel.setText("10");
        timerLabel.setForeground(new Color(180, 0, 0));
        questionActive = true;
        timer.restart();
    }

    private Color getDefaultColor(int i) {
        Color[] c = {new Color(100, 149, 237), new Color(255, 165, 0),
                new Color(144, 238, 144), new Color(255, 99, 132)};
        return c[i];
    }

    private void hostSkip() {
        if (timer.isRunning()) timer.stop();
        questionActive = false;
        new Thread(() -> server.nextQuestion()).start();
    }

    private void hostAutoAdvance() {
        new Thread(() -> server.nextQuestion()).start();
    }

    // Called by GameServer on its thread whenever it broadcasts a new question or game over
    private void onServerAdvanced() {
        SwingUtilities.invokeLater(() -> {
            if (server.isGameOver()) {
                showResults();
            } else {
                // Sync displayedQuestionIdx to what the server actually sent
                displayedQuestionIdx = server.getCurrentQuestionIdx();
                showCurrentQuestion();
            }
        });
    }

    private void showResults() {
        if (timer.isRunning()) timer.stop();
        String lb = server.getLeaderboardString();
        displayLeaderboard(lb);
        cardLayout.show(cardPanel, "RESULTS");
    }

    public void displayLeaderboard(String lbString) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s %-20s %s%n", "Rank", "Username", "Score"));
        sb.append("─".repeat(40)).append("\n");
        if (lbString == null || lbString.isEmpty()) {
            sb.append("No scores recorded yet.");
        } else {
            String[] entries = lbString.split(";");
            for (int i = 0; i < entries.length; i++) {
                String[] parts = entries[i].split(":");
                if (parts.length == 2) {
                    sb.append(String.format("%-5d %-20s %s%n", i + 1, parts[0], parts[1]));
                }
            }
        }
        leaderboardArea.setText(sb.toString());
    }

    // ---- Utilities ----

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new java.util.Date() + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void updateClientCount() {
        SwingUtilities.invokeLater(() ->
                clientCountLabel.setText("Clients connected: " + server.getClients().size()));
    }
}
