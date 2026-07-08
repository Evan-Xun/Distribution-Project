package distproject.ui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class AppTheme {
    public static final Color PENDING_COLOR = new Color(0x9E, 0xA3, 0xAC);
    public static final Color PREPARING_COLOR = new Color(0xF5, 0xA6, 0x23);
    public static final Color READY_COLOR = new Color(0x2E, 0xA6, 0x4B);
    public static final Color COMPLETED_COLOR = new Color(0x2C, 0x7B, 0xB0);
    public static final Color ERROR_COLOR = new Color(0xD9, 0x4F, 0x4F);
    public static final Color ACCENT_COLOR = new Color(0x3B, 0x6E, 0xA5);

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
            case "PREPARING" -> PREPARING_COLOR;
            case "READY" -> READY_COLOR;
            case "COMPLETED" -> COMPLETED_COLOR;
            default -> PENDING_COLOR;
        };
    }

    public static class StatusChip extends JLabel {
        private Color chipColor = PENDING_COLOR;

        public StatusChip(String text, Color color) {
            super(text, SwingConstants.CENTER);
            setColor(color);
            setFont(getFont().deriveFont(Font.BOLD, 11f));
            setForeground(Color.WHITE);
            setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 10, 3, 10));
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
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
