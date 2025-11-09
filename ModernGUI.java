import com.formdev.flatlaf.FlatDarculaLaf;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics; 
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * Головний клас програми, який створює GUI та керує логікою.
 */
public class ModernGUI {

    private JFrame frame;
    private JButton singleThreadButton;
    private JButton multiThreadButton;
    private JTextArea resultArea;
    private JProgressBar progressBar;
    
    // Власний компонент GUI для малювання графіка.
    private FinalChartPanel chartPanel;

    public ModernGUI() {
        frame = new JFrame("Лабораторна №1: Аналіз продуктивності");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(800, 500));

        JPanel mainPanel = (JPanel) frame.getContentPane();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        singleThreadButton = new JButton("Старт (1 Потік)");
        multiThreadButton = new JButton("Старт (Багато Потоків)");
        singleThreadButton.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        multiThreadButton.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        buttonPanel.add(singleThreadButton);
        buttonPanel.add(multiThreadButton);

        resultArea = new JTextArea("Натисніть кнопку, щоб почати...\n");
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(resultArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1));

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Очікування...");
        progressBar.setIndeterminate(false);

        // Ініціалізуємо власний клас для малювання.
        chartPanel = new FinalChartPanel(); 
        chartPanel.setPreferredSize(new Dimension(300, 100)); 
        
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(chartPanel, BorderLayout.CENTER);
        rightPanel.setBorder(new MatteBorder(0, 1, 0, 0, UIManager.getColor("Component.borderColor")));

        mainPanel.add(buttonPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(progressBar, BorderLayout.SOUTH);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        setupButtonListeners();
    }

    public void show() {
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * Прив'язує логіку до кнопок.
     * Використовує лямбда-вирази для короткого запису ActionListener.
     */
    private void setupButtonListeners() {
        singleThreadButton.addActionListener(e -> runTask(false));
        multiThreadButton.addActionListener(e -> runTask(true));
    }

    /**
     * Це головний метод, що керує логікою виконання задачі.
     * Він використовує SwingWorker, щоб виконати роботу у фоні.
     */
    private void runTask(boolean isMultiThreaded) {
        String taskName = isMultiThreaded ? "Багатопоточна" : "Однопоточна";
        
        // Блокуємо кнопки, щоб уникнути повторних запусків
        singleThreadButton.setEnabled(false);
        multiThreadButton.setEnabled(false);
        resultArea.append("Починаємо " + taskName + " задачу...\n");
        progressBar.setIndeterminate(true);
        progressBar.setString("Виконання...");

        /**
         * SwingWorker - це інструмент для багатопоточності в Swing.
         * Він дозволяє чітко розділити логіку на два потоки:
         * 1. doInBackground() - виконується у фоновому потоці (тут рахуємо).
         * 2. done() - виконується у потоці GUI (тут оновлюємо інтерфейс).
         */
        SwingWorker<ResultData, Void> worker = new SwingWorker<ResultData, Void>() {
            
            /**
             * Цей код виконується у ФОНОВОМУ ПОТОЦІ.
             * Він не повинен торкатися компонентів GUI (кнопок, тексту).
             */
            @Override
            protected ResultData doInBackground() throws Exception {
                long d1 = System.currentTimeMillis();
                Long summa;

                if (isMultiThreaded) {
                    // Запускаємо багатопоточну версію
                    BigTaskManyThreads bt = new BigTaskManyThreads();
                    summa = bt.startTask();
                } else {
                    // Запускаємо однопоточну версію
                    BigTaskOneThread bt = new BigTaskOneThread();
                    summa = bt.startTask();
                }
                long d2 = System.currentTimeMillis();
                long timeTaken = d2 - d1;
                
                // Повертаємо результат у вигляді спеціального об'єкта
                return new ResultData(summa, timeTaken);
            }
            
            /**
             * Цей код виконується у ГОЛОВНОМУ ПОТОЦІ GUI (Event Dispatch Thread).
             * Він запускається АВТОМАТИЧНО, коли doInBackground() завершується.
             * Тільки звідси можна безпечно оновлювати GUI.
             */
            @Override
            protected void done() {
                try {
                    // get() - отримує результат, який повернув doInBackground()
                    ResultData result = get();
                    String resultText = String.format("Результат: %d, Час: %d мс", result.summa, result.timeTaken);
                    
                    // БЕЗПЕЧНО оновлюємо GUI
                    resultArea.append(taskName + " задача завершена.\n");
                    resultArea.append(resultText + "\n\n");
                    
                    // Посилаємо дані на наш графік для перемальовки
                    chartPanel.updateData(isMultiThreaded, result.timeTaken);
                    
                } catch (Exception ex) {
                    resultArea.append("Помилка: " + ex.getMessage() + "\n");
                }
                
                // Повертаємо кнопки та прогрес-бар у вихідний стан
                singleThreadButton.setEnabled(true);
                multiThreadButton.setEnabled(true);
                progressBar.setIndeterminate(false);
                progressBar.setString("Очікування...");
            }
        };
        
        // Запускає SwingWorker (тобто, викликає doInBackground() у фоні)
        worker.execute();
    }

    /**
     * Простий клас-контейнер для передачі ДВОХ значень (summa, timeTaken)
     * з фонового потоку у головний.
     */
    private class ResultData {
        public long summa; 
        public long timeTaken;
        public ResultData(long summa, long timeTaken) { this.summa = summa; this.timeTaken = timeTaken; }
    }

    /**
     * Головна точка входу в програму.
     */
    public static void main(String[] args) {
        try {
            // ЛОГІКА СТИЛЮ: Вмикаємо професійну темну тему FlatLaf.
            // Це потрібно зробити ДО створення будь-яких компонентів Swing.
            FlatDarculaLaf.setup();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // ЛОГІКА GUI: Запускаємо створення GUI у спеціальному, безпечному
        // потоці "Event Dispatch Thread" (EDT). Це стандарт для Swing.
        SwingUtilities.invokeLater(() -> {
            ModernGUI gui = new ModernGUI();
            gui.show();
        });
    }
    
    // ====================================================================
    // ЛОГІКА ЗАВДАННЯ: Класи, що виконують обчислення
    // ====================================================================
    
    public static final int STR_COUNT = 100; // Кількість завдань
    public static final int TASK_COUNT = 10000; // "Складність" одного завдання

    /**
     * Імітація важкої роботи (генерування великих чисел та підрахунок).
     */
    public Long process() { 
        Long summa = 0L; 
        SecureRandom random = new SecureRandom(); 
        for (int i = 0; i < TASK_COUNT; i++) { 
            String g = new BigInteger(500, random).toString(32); 
            for (char c : g.toCharArray()) { 
                summa += c; 
            } 
        } 
        return summa; 
    }

    /**
     * ОДНОПОТОЧНА ЛОГІКА:
     * Простий цикл, який 100 разів поспіль виконує важку роботу.
     */
    class BigTaskOneThread { 
        public Long startTask() { 
            Long summa = 0L; 
            for (int i = 0; i < STR_COUNT; i++) { 
                summa += process(); 
            } 
            return summa; 
        } 
    }
    
    /**
     * БАГАТОПОТОЧНА ЛОГІКА:
     * Сучасний підхід до паралелізму.
     */
    class BigTaskManyThreads { 
        public Long startTask() { 
            // Визначаємо, скільки потоків (ядер) є в системі
            int ap = Runtime.getRuntime().availableProcessors();
            
            // Створюємо пул потоків, який керуватиме нашими ядрами
            ExecutorService es = Executors.newFixedThreadPool(ap); 
            
            Long summa = 0L; 
            try { 
                // Створюємо список зі 100 "завдань"
                List<MyCallable> threads = new ArrayList<MyCallable>(); 
                for (int i = 0; i < STR_COUNT; i++) { 
                    threads.add(new MyCallable()); 
                } 
                
                // Команда: "Виконати всі завдання з цього списку паралельно"
                // Повертає список "майбутніх" результатів (Future)
                List<Future<Long>> result = es.invokeAll(threads);
                
                // Збираємо результати з кожного "майбутнього"
                for (Future<Long> f : result) { 
                    summa += f.get(); // f.get() - чекає, поки потік завершиться, і дає результат
                }
                
            } catch (InterruptedException | ExecutionException ex) { 
                ex.printStackTrace(System.out); 
            } finally {
                es.shutdown(); // Закриваємо пул потоків
            }
            return summa; 
        } 
    }
    
    /**
     * ЗАВДАННЯ (TASK):
     * Це клас, що реалізує інтерфейс Callable.
     * Він є "одиницею" роботи. ExecutorService бере 100 таких завдань
     * і "роздає" їх вільним потокам у пулі.
     * Callable, на відміну від Runnable, може повертати результат (Long).
     */
    class MyCallable implements Callable<Long> { 
        @Override 
        public Long call() throws Exception { 
            return process(); // Кожен потік просто виконує роботу
        } 
    }

    // ====================================================================
    // ЛОГІКА ГРАФІКА: Компонент, що малює стовпці
    // ====================================================================

    class FinalChartPanel extends JPanel {
        
        private long singleThreadTime = 0;
        private long multiThreadTime = 0;

        private Color barColor1 = new Color(75, 172, 250); 
        private Color barColor2 = new Color(110, 200, 120); 
        private Color axisColor = UIManager.getColor("Component.borderColor");
        private Color labelColor = UIManager.getColor("Label.foreground");
        private Color valueColor = UIManager.getColor("Label.foreground");

        public FinalChartPanel() {
            setBorder(BorderFactory.createTitledBorder("Порівняння"));
        }

        /**
         * Цей метод викликається з потоку GUI (з методу done() у SwingWorker),
         * щоб передати нові дані для графіка.
         */
        public void updateData(boolean isMultiThreaded, long time) {
            if (time <= 0) time = 1;
            
            if (isMultiThreaded) {
                this.multiThreadTime = time;
            } else {
                this.singleThreadTime = time;
            }
            
            // Ключова команда: "Система, перемалюй цей компонент"
            // Це автоматично викличе paintComponent()
            repaint(); 
        }

        /**
         * Головний метод малювання. Swing викликає його, коли компонент
         * потрібно оновити (наприклад, після repaint()).
         */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); 
            
            // Використовуємо Graphics2D для якіснішого малювання (згладжування)
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Insets insets = getInsets();
            int width = getWidth() - insets.left - insets.right;
            int height = getHeight() - insets.top - insets.bottom;
            
            // Розраховуємо координати "підлоги" та "стелі" для графіка
            int yFloor = height - 40; 
            int yCeiling = 50; 
            int maxBarHeight = yFloor - yCeiling;

            g2.setColor(axisColor);
            g2.drawLine(insets.left + 20, yFloor, width - 20, yFloor);
            
            // ЛОГІКА МАСШТАБУВАННЯ:
            // Визначаємо найдовший час, щоб він був 100% висоти (maxBarHeight)
            long maxTime = Math.max(1, Math.max(singleThreadTime, multiThreadTime));
            
            int h1 = (int)(maxBarHeight * ((double)singleThreadTime / maxTime));
            int h2 = (int)(maxBarHeight * ((double)multiThreadTime / maxTime));

            int barWidth = width / 5;
            int x1 = width / 4 - barWidth / 2 + 10;
            int x2 = (3 * width / 4) - barWidth / 2 - 10;

            // Малюємо стовпці
            g2.setColor(barColor1);
            g2.fillRect(x1, yFloor - h1, barWidth, h1);
            
            g2.setColor(barColor2);
            g2.fillRect(x2, yFloor - h2, barWidth, h2);

            FontMetrics metrics;
            
            // Малюємо підписи під стовпцями
            g2.setColor(labelColor);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            metrics = g2.getFontMetrics();
            
            String label1 = "1 Потік";
            int label1X = x1 + barWidth/2 - metrics.stringWidth(label1)/2;
            g2.drawString(label1, label1X, yFloor + 15);
            
            String label2 = "Багато потоків";
            int label2X = x2 + barWidth/2 - metrics.stringWidth(label2)/2;
            g2.drawString(label2, label2X, yFloor + 15);
            
            // Малюємо значення (час у мс)
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            metrics = g2.getFontMetrics();

            if (singleThreadTime > 0) {
                String val1 = singleThreadTime + " мс";
                int val1X = x1 + barWidth/2 - metrics.stringWidth(val1)/2;
                int val1Y = yFloor - h1 - 5; 
                
                // ЛОГІКА ВИПРАВЛЕННЯ НАКЛАДАННЯ:
                // Якщо текст вилазить за "стелю" (yCeiling)...
                if (val1Y < yCeiling) { 
                    // ...малюємо його всередині стовпця (знизу)
                    val1Y = yFloor - h1 + metrics.getHeight(); 
                    g2.setColor(Color.BLACK); // Робимо текст темним для читабельності
                } else {
                    g2.setColor(valueColor); // Інакше малюємо над стовпцем
                }
                g2.drawString(val1, val1X, val1Y);
            }
            
            if (multiThreadTime > 0) {
                String val2 = multiThreadTime + " мс";
                int val2X = x2 + barWidth/2 - metrics.stringWidth(val2)/2;
                int val2Y = yFloor - h2 - 5;
                
                if (val2Y < yCeiling) {
                    val2Y = yFloor - h2 + metrics.getHeight();
                    g2.setColor(Color.BLACK);
                } else {
                     g2.setColor(valueColor);
                }
                g2.drawString(val2, val2X, val2Y);
            }

            // Малюємо головний результат - "Прискорення"
            if (singleThreadTime > 0 && multiThreadTime > 0) {
                double speedup = (double)singleThreadTime / multiThreadTime;
                String speedupText = String.format("Прискорення: x%.1f", speedup);
                
                g2.setColor(labelColor);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                metrics = g2.getFontMetrics();
                
                int speedupX = width/2 - metrics.stringWidth(speedupText)/2;
                g2.drawString(speedupText, speedupX, yCeiling - 10);
            }
        }
    }

}
