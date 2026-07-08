package distproject.ui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class AppTheme {
    public static final Color PENDING_COLOR = new Color(0x9E, 0xA3, 0xAC);
    public static final Color PREPARING_COLOR = new Color(0xF5, 0xA6, 0x23);
    public static final Color READY_COLOR = new Color(0x2C, 0x7B, 0xE5);
    public static final Color COMPLETED_COLOR = new Color(0x2E, 0xA6, 0x4B);
    public static final Color ERROR_COLOR = new Color(0xD9, 0x4F, 0x4F);
    public static final Color ACCENT_COLOR = new Color(0x3B, 0x6E, 0xA5);
    private static final Color INACTIVE_PROGRESS_COLOR = new Color(0xD7, 0xDB, 0xE2);
    private static final Color PROGRESS_LINE_COLOR = new Color(0xC7, 0xCC, 0xD4);

    private AppTheme() {
    }

    public static void applyTheme() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception ignored) {
            // Falls back to the platform default look and feel.
        }
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 12);
    }

    public static Color colorForStatus(String status) {
        return switch (status) {
            case "PROCESSING" -> PREPARING_COLOR;
            case "PREPARING" -> PREPARING_COLOR;
            case "READY" -> READY_COLOR;
            case "COMPLETED" -> COMPLETED_COLOR;
            default -> PENDING_COLOR;
        };
    }

    public static class StatusChip extends JLabel {
        private static final int CHIP_HEIGHT = 28;
        private Color chipColor = PENDING_COLOR;

        public StatusChip(String text, Color color) {
            super(text, SwingConstants.CENTER);
            setColor(color);
            setFont(getFont().deriveFont(Font.BOLD, 11f));
            setForeground(Color.WHITE);
            setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 10, 3, 10));
            setPreferredSize(new Dimension(96, CHIP_HEIGHT));
            setMinimumSize(new Dimension(88, CHIP_HEIGHT));
            setMaximumSize(new Dimension(104, CHIP_HEIGHT));
        }

        public void setColor(Color color) {
            this.chipColor = color;
            repaint();
        }

        public void setStatusText(String text, Color color) {
            setText(text);
            setColor(color);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(chipColor);
            int chipY = Math.max(0, (getHeight() - CHIP_HEIGHT) / 2);
            g2.fillRoundRect(0, chipY, getWidth() - 1, CHIP_HEIGHT - 1, CHIP_HEIGHT, CHIP_HEIGHT);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class ItemProgress extends JPanel {
        private static final String[] STATUSES = {"PENDING", "PREPARING", "READY", "COMPLETED"};
        private final String status;

        public ItemProgress(String status) {
            this.status = status == null ? "PENDING" : status;
            setOpaque(false);
            setPreferredSize(new Dimension(210, 28));
            setMinimumSize(new Dimension(190, 28));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int currentIndex = statusIndex(status);
            int startX = 12;
            int y = getHeight() / 2;
            int gap = 26;
            int dotSize = 10;
            int currentDotSize = 13;

            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int index = 0; index < STATUSES.length - 1; index++) {
                int x1 = startX + index * gap + dotSize / 2;
                int x2 = startX + (index + 1) * gap + dotSize / 2;
                g2.setColor(index < currentIndex ? PROGRESS_LINE_COLOR : INACTIVE_PROGRESS_COLOR);
                g2.drawLine(x1, y, x2, y);
            }

            for (int index = 0; index < STATUSES.length; index++) {
                int size = index == currentIndex ? currentDotSize : dotSize;
                int x = startX + index * gap + dotSize / 2 - size / 2;
                int dotY = y - size / 2;
                boolean reached = index <= currentIndex;

                g2.setColor(reached ? colorForStatus(STATUSES[index]) : INACTIVE_PROGRESS_COLOR);
                g2.fillOval(x, dotY, size, size);
                if (!reached) {
                    g2.setColor(new Color(0xB9, 0xBF, 0xC8));
                    g2.drawOval(x, dotY, size, size);
                }
            }

            g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
            g2.setColor(colorForStatus(status));
            g2.drawString(status, startX + STATUSES.length * gap + 10, y + 4);
            g2.dispose();
        }

        private int statusIndex(String status) {
            for (int index = 0; index < STATUSES.length; index++) {
                if (STATUSES[index].equals(status)) {
                    return index;
                }
            }
            return 0;
        }
    }
}
