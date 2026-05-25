import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;

public class ClientApp extends JFrame {
    // Server connection
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // Cards
    private JPanel cardPanel;
    private CardLayout cardLayout;

    // Login panel
    private JTextField loginUsernameField;
    private JPasswordField loginPasswordField;
    private JLabel loginMessageLabel;

    // Register panel
    private JTextField regUsernameField;
    private JPasswordField regPasswordField;
    private JLabel regMessageLabel;

    // Game panel
    private JLabel questionLabel;
    private JButton[] optionButtons = new JButton[4];
    private JLabel timerLabel;
    private JLabel scoreLabel;
    private JLabel questionCountLabel;
    private Timer clientTimer;
    private int clientTimeLeft;
    private int clientScore = 0;
    private int totalQuestions = 10;
    private int currentQIdx = 0;
    private boolean answered = false;
    private int lastAnswerIdx = -1; // tracks which button client picked this round

    // Results panel
    private JTextArea leaderboardArea;
    private JLabel finalScoreLabel;

    public ClientApp() {
        setTitle("Trivia Game - Client");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        buildConnectingPanel();
        buildLoginPanel();
        buildRegisterPanel();
        buildGamePanel();
        buildResultsPanel();

        add(cardPanel);

        // Connect in background
        new Thread(this::connectToServer).start();
    }

    // ---- Server Connection ----

    private void connectToServer() {
        SwingUtilities.invokeLater(() -> cardLayout.show(cardPanel, "CONNECTING"));
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            SwingUtilities.invokeLater(() -> cardLayout.show(cardPanel, "LOGIN"));
            // Start listening for server messages
            listenToServer();
        } catch (IOException e) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this,
                    "Could not connect to server at " + SERVER_HOST + ":" + SERVER_PORT +
                    "\nMake sure the server is running first.",
                    "Connection Failed", JOptionPane.ERROR_MESSAGE));
        }
    }

    private void listenToServer() {
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> handleServerMessage(msg));
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, "Disconnected from server.", "Connection Lost", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    private void handleServerMessage(String msg) {
        String[] parts = msg.split("\\|", -1);
        if (parts.length == 0) return;

        switch (parts[0]) {
            case "LOGIN_OK":
                clientScore = 0;
                scoreLabel.setText("Score: 0");
                cardLayout.show(cardPanel, "GAME");
                break;
            case "LOGIN_FAIL":
                loginMessageLabel.setText(parts.length > 1 ? parts[1] : "Login failed");
                loginMessageLabel.setForeground(Color.RED);
                break;
            case "REGISTER_OK":
                regMessageLabel.setText("Registration successful! Please log in.");
                regMessageLabel.setForeground(new Color(0, 150, 0));
                break;
            case "REGISTER_FAIL":
                regMessageLabel.setText(parts.length > 1 ? parts[1] : "Registration failed");
                regMessageLabel.setForeground(Color.RED);
                break;
            case "GAME_RESET":
                clientScore = 0;
                scoreLabel.setText("Score: 0");
                break;
            case "QUESTION":
                // QUESTION|idx|total|question|opt0|opt1|opt2|opt3
                if (parts.length >= 8) {
                    int idx = Integer.parseInt(parts[1]);
                    int total = Integer.parseInt(parts[2]);
                    currentQIdx = idx;
                    totalQuestions = total;
                    showQuestion(idx, total, parts[3], parts[4], parts[5], parts[6], parts[7]);
                }
                break;
            case "ANSWER_RESULT":
                // ANSWER_RESULT|CORRECT/WRONG|correctIndex
                if (parts.length >= 3) {
                    boolean correct = parts[1].equals("CORRECT");
                    int correctIdx = Integer.parseInt(parts[2]);
                    highlightAnswer(correct, correctIdx);
                    if (correct) {
                        clientScore++;
                        scoreLabel.setText("Score: " + clientScore);
                    }
                }
                break;
            case "GAME_OVER":
                // GAME_OVER|leaderboardString
                if (clientTimer != null && clientTimer.isRunning()) clientTimer.stop();
                String lb = parts.length > 1 ? parts[1] : "";
                showResults(lb);
                break;
            default:
                System.out.println("Unknown server message: " + msg);
        }
    }

    private void sendToServer(String msg) {
        if (out != null) out.println(msg);
    }

    // ---- Panel Builders ----

    private void buildConnectingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 30, 60));
        JLabel label = new JLabel("Connecting to server...", SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 26));
        label.setForeground(Color.WHITE);
        panel.add(label, BorderLayout.CENTER);
        cardPanel.add(panel, "CONNECTING");
    }

    private void buildLoginPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(173, 216, 230));

        JLabel title = new JLabel("Trivia Game Login", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 36));
        title.setBorder(new EmptyBorder(30, 0, 10, 0));
        panel.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(20, 100, 20, 100));

        loginUsernameField = new JTextField();
        loginPasswordField = new JPasswordField();
        loginMessageLabel = new JLabel(" ", SwingConstants.CENTER);
        loginMessageLabel.setFont(new Font("Arial", Font.BOLD, 14));

        addFormRow(form, "Username:", loginUsernameField);
        form.add(Box.createRigidArea(new Dimension(0, 10)));
        addFormRow(form, "Password:", loginPasswordField);
        form.add(Box.createRigidArea(new Dimension(0, 15)));
        form.add(loginMessageLabel);
        form.add(Box.createRigidArea(new Dimension(0, 15)));

        JButton loginBtn = new JButton("Login");
        loginBtn.setFont(new Font("Arial", Font.BOLD, 20));
        loginBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginBtn.addActionListener(e -> doLogin());
        form.add(loginBtn);

        form.add(Box.createRigidArea(new Dimension(0, 15)));
        JLabel regLink = new JLabel("Don't have an account? Register here", SwingConstants.CENTER);
        regLink.setFont(new Font("Arial", Font.PLAIN, 14));
        regLink.setForeground(new Color(0, 0, 180));
        regLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        regLink.setAlignmentX(Component.CENTER_ALIGNMENT);
        regLink.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { cardLayout.show(cardPanel, "REGISTER"); }
        });
        form.add(regLink);

        panel.add(form, BorderLayout.CENTER);
        cardPanel.add(panel, "LOGIN");
    }

    private void buildRegisterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(173, 216, 230));

        JLabel title = new JLabel("Create an Account", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 36));
        title.setBorder(new EmptyBorder(30, 0, 10, 0));
        panel.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(20, 100, 20, 100));

        regUsernameField = new JTextField();
        regPasswordField = new JPasswordField();
        regMessageLabel = new JLabel(" ", SwingConstants.CENTER);
        regMessageLabel.setFont(new Font("Arial", Font.BOLD, 14));

        addFormRow(form, "Username:", regUsernameField);
        form.add(Box.createRigidArea(new Dimension(0, 10)));
        addFormRow(form, "Password:", regPasswordField);
        form.add(Box.createRigidArea(new Dimension(0, 15)));
        form.add(regMessageLabel);
        form.add(Box.createRigidArea(new Dimension(0, 15)));

        JButton regBtn = new JButton("Register");
        regBtn.setFont(new Font("Arial", Font.BOLD, 20));
        regBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        regBtn.addActionListener(e -> doRegister());
        form.add(regBtn);

        form.add(Box.createRigidArea(new Dimension(0, 15)));
        JLabel loginLink = new JLabel("Already have an account? Login here", SwingConstants.CENTER);
        loginLink.setFont(new Font("Arial", Font.PLAIN, 14));
        loginLink.setForeground(new Color(0, 0, 180));
        loginLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loginLink.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginLink.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { cardLayout.show(cardPanel, "LOGIN"); }
        });
        form.add(loginLink);

        panel.add(form, BorderLayout.CENTER);
        cardPanel.add(panel, "REGISTER");
    }

    private void buildGamePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(173, 216, 230));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        questionCountLabel = new JLabel("Question 1/10", SwingConstants.LEFT);
        questionCountLabel.setFont(new Font("Arial", Font.BOLD, 16));
        header.add(questionCountLabel, BorderLayout.WEST);

        scoreLabel = new JLabel("Score: 0", SwingConstants.CENTER);
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 16));
        header.add(scoreLabel, BorderLayout.CENTER);

        timerLabel = new JLabel("10", SwingConstants.RIGHT);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 32));
        timerLabel.setForeground(new Color(180, 0, 0));
        header.add(timerLabel, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);

        // Question
        questionLabel = new JLabel("Waiting for question...", SwingConstants.CENTER);
        questionLabel.setFont(new Font("Arial", Font.BOLD, 22));
        questionLabel.setBorder(new EmptyBorder(20, 10, 20, 10));
        panel.add(questionLabel, BorderLayout.CENTER);

        // Options
        JPanel btnPanel = new JPanel(new GridLayout(2, 2, 15, 15));
        btnPanel.setOpaque(false);
        Color[] btnColors = {new Color(100, 149, 237), new Color(255, 165, 0),
                new Color(144, 238, 144), new Color(255, 99, 132)};
        for (int i = 0; i < 4; i++) {
            optionButtons[i] = new JButton("Option " + (i + 1));
            optionButtons[i].setFont(new Font("Arial", Font.BOLD, 22));
            optionButtons[i].setBackground(btnColors[i]);
            optionButtons[i].setOpaque(true);
            int idx = i;
            optionButtons[i].addActionListener(e -> submitAnswer(idx));
            btnPanel.add(optionButtons[i]);
        }
        panel.add(btnPanel, BorderLayout.SOUTH);

        // Client-side timer: purely cosmetic countdown, NEVER advances the question.
        // Only the server's QUESTION message triggers moving to the next question.
        clientTimer = new Timer(1000, e -> {
            clientTimeLeft--;
            timerLabel.setText(String.valueOf(clientTimeLeft));
            if (clientTimeLeft <= 5) timerLabel.setForeground(Color.RED);
            if (clientTimeLeft <= 0) {
                clientTimer.stop();
                setButtonsEnabled(false);
                questionLabel.setText("<html><div style='text-align:center;'>Time's up! Waiting for server...</div></html>");
            }
        });

        cardPanel.add(panel, "GAME");
    }

    private void buildResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(30, 30, 60));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("🏆 Game Over!", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 36));
        title.setForeground(Color.YELLOW);
        panel.add(title, BorderLayout.NORTH);

        finalScoreLabel = new JLabel("Your Score: 0", SwingConstants.CENTER);
        finalScoreLabel.setFont(new Font("Arial", Font.BOLD, 22));
        finalScoreLabel.setForeground(Color.WHITE);

        leaderboardArea = new JTextArea();
        leaderboardArea.setEditable(false);
        leaderboardArea.setFont(new Font("Monospaced", Font.PLAIN, 16));
        leaderboardArea.setBackground(new Color(20, 20, 40));
        leaderboardArea.setForeground(Color.WHITE);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(new Color(30, 30, 60));
        centerPanel.add(finalScoreLabel, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(leaderboardArea), BorderLayout.CENTER);
        panel.add(centerPanel, BorderLayout.CENTER);

        cardPanel.add(panel, "RESULTS");
    }

    // ---- Game Actions ----

    private void doLogin() {
        String user = loginUsernameField.getText().trim();
        String pass = new String(loginPasswordField.getPassword()).trim();
        if (user.isEmpty() || pass.isEmpty()) {
            loginMessageLabel.setText("Please enter both username and password.");
            loginMessageLabel.setForeground(Color.RED);
            return;
        }
        loginMessageLabel.setText("Logging in...");
        loginMessageLabel.setForeground(Color.DARK_GRAY);
        sendToServer("LOGIN|" + user + "|" + pass);
    }

    private void doRegister() {
        String user = regUsernameField.getText().trim();
        String pass = new String(regPasswordField.getPassword()).trim();
        if (user.isEmpty() || pass.isEmpty()) {
            regMessageLabel.setText("Please enter both username and password.");
            regMessageLabel.setForeground(Color.RED);
            return;
        }
        regMessageLabel.setText("Registering...");
        regMessageLabel.setForeground(Color.DARK_GRAY);
        sendToServer("REGISTER|" + user + "|" + pass);
    }

    private void showQuestion(int idx, int total, String question, String opt0, String opt1, String opt2, String opt3) {
        questionCountLabel.setText("Question " + (idx + 1) + " / " + total);
        questionLabel.setText("<html><div style='text-align:center;'>" + question + "</div></html>");
        optionButtons[0].setText(opt0);
        optionButtons[1].setText(opt1);
        optionButtons[2].setText(opt2);
        optionButtons[3].setText(opt3);

        // Reset button colors
        Color[] btnColors = {new Color(100, 149, 237), new Color(255, 165, 0),
                new Color(144, 238, 144), new Color(255, 99, 132)};
        for (int i = 0; i < 4; i++) {
            optionButtons[i].setBackground(btnColors[i]);
            optionButtons[i].setEnabled(true);
        }

        answered = false;
        lastAnswerIdx = -1;
        clientTimeLeft = 10;
        timerLabel.setText("10");
        timerLabel.setForeground(new Color(180, 0, 0));
        if (clientTimer.isRunning()) clientTimer.stop();
        clientTimer.restart();

        cardLayout.show(cardPanel, "GAME");
    }

    private void submitAnswer(int idx) {
        if (answered) return;
        answered = true;
        lastAnswerIdx = idx;
        if (clientTimer.isRunning()) clientTimer.stop();
        setButtonsEnabled(false);
        sendToServer("ANSWER|" + idx);
    }

    private void highlightAnswer(boolean wasCorrect, int correctIdx) {
        // Always highlight correct answer green
        optionButtons[correctIdx].setBackground(new Color(0, 200, 80));

        // If player answered and got it wrong, highlight their pick red
        if (!wasCorrect && lastAnswerIdx >= 0 && lastAnswerIdx != correctIdx) {
            optionButtons[lastAnswerIdx].setBackground(new Color(220, 50, 50));
        }

        // Dim all other buttons that aren't highlighted
        for (int i = 0; i < 4; i++) {
            if (i != correctIdx && i != lastAnswerIdx) {
                optionButtons[i].setBackground(new Color(180, 180, 180));
            }
        }

        // Show "Waiting for next question..." in the question label
        // so the player knows to wait for the server
        String resultText = wasCorrect ? "✓ Correct! Waiting for server..." : "✗ Wrong! Waiting for server...";
        timerLabel.setText(resultText.startsWith("✓") ? "✓" : "✗");
        timerLabel.setForeground(wasCorrect ? new Color(0, 180, 0) : new Color(200, 0, 0));
    }

    private void showResults(String lbString) {
        finalScoreLabel.setText("Your Score: " + clientScore + " / " + totalQuestions);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s %-20s %s%n", "Rank", "Username", "Score"));
        sb.append("─".repeat(40)).append("\n");
        if (lbString == null || lbString.isEmpty()) {
            sb.append("No scores recorded yet.");
        } else {
            String[] entries = lbString.split(";");
            for (int i = 0; i < entries.length; i++) {
                String[] p = entries[i].split(":");
                if (p.length == 2) {
                    sb.append(String.format("%-5d %-20s %s%n", i + 1, p[0], p[1]));
                }
            }
        }
        leaderboardArea.setText(sb.toString());
        cardLayout.show(cardPanel, "RESULTS");
    }

    private void setButtonsEnabled(boolean enabled) {
        for (JButton b : optionButtons) b.setEnabled(enabled);
    }

    // ---- Form Helper ----

    private void addFormRow(JPanel parent, String labelText, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Arial", Font.BOLD, 16));
        lbl.setPreferredSize(new Dimension(100, 30));
        field.setFont(new Font("Arial", Font.PLAIN, 16));
        row.add(lbl, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        parent.add(row);
    }
}
