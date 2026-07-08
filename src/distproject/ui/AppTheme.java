package distproject.ui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.JTableHeader;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class AppTheme {
    public static final Color APP_BACKGROUND = new Color(0xF4, 0xF6, 0xF8);
    public static final Color PANEL_BACKGROUND = new Color(0xFF, 0xFF, 0xFF);
    public static final Color SURFACE_BACKGROUND = new Color(0xFB, 0xFC, 0xFD);
    public static final Color FIELD_BACKGROUND = new Color(0xFF, 0xFF, 0xFF);
    public static final Color BORDER_COLOR = new Color(0xDA, 0xDE, 0xE6);
    public static final Color TABLE_HEADER_BACKGROUND = new Color(0xEA, 0xEF, 0xF5);
    public static final Color TEXT_PRIMARY = new Color(0x1F, 0x29, 0x37);
    public static final Color TEXT_SECONDARY = new Color(0x6B, 0x72, 0x80);
    public static final Color LOG_BACKGROUND = new Color(0x11, 0x18, 0x27);
    public static final Color LOG_FOREGROUND = new Color(0xD1, 0xD5, 0xDB);
    public static final Color PENDING_COLOR = new Color(0x9A, 0xA1, 0xAD);
    public static final Color PREPARING_COLOR = new Color(0xD8, 0x96, 0x32);
    public static final Color READY_COLOR = new Color(0x5B, 0x83, 0xBE);
    public static final Color COMPLETED_COLOR = new Color(0x58, 0x95, 0x68);
    public static final Color ERROR_COLOR = new Color(0xBE, 0x64, 0x64);
    public static final Color ACCENT_COLOR = new Color(0x5B, 0x7F, 0xB8);
    public static final Color SOFT_BLUE = new Color(0xE9, 0xF1, 0xFB);
    public static final Color SOFT_GREEN = new Color(0xEA, 0xF5, 0xEE);
    public static final Color SOFT_ORANGE = new Color(0xFB, 0xF1, 0xE3);
    public static final Color SOFT_RED = new Color(0xFA, 0xEE, 0xEE);
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
        UIManager.put("Panel.background", APP_BACKGROUND);
        UIManager.put("Table.selectionBackground", new Color(0xDB, 0xE8, 0xFF));
        UIManager.put("Table.selectionForeground", TEXT_PRIMARY);
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

    public static void styleTextField(JTextField textField) {
        textField.setBackground(FIELD_BACKGROUND);
        textField.setForeground(TEXT_PRIMARY);
        textField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
    }

    public static void styleTextArea(JTextArea textArea, boolean dark) {
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setBackground(dark ? LOG_BACKGROUND : SURFACE_BACKGROUND);
        textArea.setForeground(dark ? LOG_FOREGROUND : TEXT_PRIMARY);
        textArea.setCaretColor(dark ? LOG_FOREGROUND : TEXT_PRIMARY);
        textArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    }

    public static void styleButton(AbstractButton button, Color background, Color foreground) {
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(background.darker()),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
    }

    public static void styleSoftButton(AbstractButton button, Color background, Color foreground, Color borderColor) {
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
    }

    public static void styleSecondaryButton(AbstractButton button) {
        styleButton(button, new Color(0xF8, 0xFA, 0xFC), TEXT_PRIMARY);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
    }

    public static void styleTable(JTable table) {
        table.setRowHeight(28);
        table.setGridColor(new Color(0xEC, 0xEF, 0xF3));
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setBackground(PANEL_BACKGROUND);
        table.setForeground(TEXT_PRIMARY);
        table.setSelectionBackground(new Color(0xDB, 0xE8, 0xFF));
        table.setSelectionForeground(TEXT_PRIMARY);

        JTableHeader header = table.getTableHeader();
        header.setBackground(TABLE_HEADER_BACKGROUND);
        header.setForeground(TEXT_PRIMARY);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
    }

    public static void stylePane(JScrollPane scrollPane, String title) {
        scrollPane.getViewport().setBackground(PANEL_BACKGROUND);
        scrollPane.setBackground(PANEL_BACKGROUND);
        scrollPane.setBorder(titledBorder(title));
    }

    public static Border titledBorder(String title) {
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                title);
        titledBorder.setTitleFont(titledBorder.getTitleFont().deriveFont(Font.BOLD, 13f));
        titledBorder.setTitleColor(TEXT_PRIMARY);
        return BorderFactory.createCompoundBorder(
                titledBorder,
                BorderFactory.createEmptyBorder(4, 4, 4, 4));
    }

    public static void stylePanel(JComponent component) {
        component.setBackground(PANEL_BACKGROUND);
        component.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
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
