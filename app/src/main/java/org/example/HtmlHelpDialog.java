package org.example;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;

public final class HtmlHelpDialog extends JDialog {
    private final HelpContent content;
    private final JEditorPane editor = new JEditorPane();
    private final JLabel pageLabel = new JLabel();
    private final JButton previousButton = new JButton("Previous");
    private final JButton nextButton = new JButton("Next");
    private int pageIndex;

    public HtmlHelpDialog(Frame owner, HelpContent content) {
        super(owner, content.title(), false);
        this.content = content;
        setLayout(new BorderLayout(8, 8));
        setMinimumSize(new Dimension(620, 520));
        setPreferredSize(new Dimension(720, 620));

        editor.setContentType("text/html");
        editor.setEditable(false);
        // The JSON lives in the classpath, so relative image URLs need an explicit base URL.
        // This keeps help files portable while still allowing images such as <img src='../test_sprite/chips.png'>.
        ((HTMLDocument) editor.getDocument()).setBase(content.baseUrl());
        editor.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getURL() != null) {
                editor.setText(wrapHtml("<p>External link:</p><p>" + event.getURL() + "</p>"));
            }
        });

        add(new JScrollPane(editor), BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);
        previousButton.addActionListener(event -> showPage(pageIndex - 1));
        nextButton.addActionListener(event -> showPage(pageIndex + 1));
        showPage(0);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(pageLabel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(event -> dispose());
        buttons.add(previousButton);
        buttons.add(nextButton);
        buttons.add(closeButton);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private void showPage(int nextIndex) {
        pageIndex = Math.max(0, Math.min(content.pages().size() - 1, nextIndex));
        HelpContent.Page page = content.pages().get(pageIndex);
        setTitle(content.title() + " - " + page.title());
        editor.setText(wrapHtml(page.html()));
        editor.setCaretPosition(0);
        previousButton.setEnabled(pageIndex > 0);
        nextButton.setEnabled(pageIndex < content.pages().size() - 1);
        pageLabel.setText("Page " + (pageIndex + 1) + " / " + content.pages().size());
    }

    private String wrapHtml(String html) {
        String body = html == null ? "" : html.strip();
        if (body.toLowerCase(java.util.Locale.ROOT).startsWith("<html")) {
            return body;
        }
        return "<html><body style='font-family:sans-serif;font-size:12px'>" + body + "</body></html>";
    }
}
