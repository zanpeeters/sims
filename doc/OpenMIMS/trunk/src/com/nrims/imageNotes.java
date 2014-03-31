package com.nrims;

/**
 * The imageNotes class provides the user with an interface
 * for writing and storing notes.
 *
 * @author cpoczatek
 */
public class imageNotes extends com.nrims.PlugInJFrame {

    java.awt.Frame instance;
    private javax.swing.JTextArea textArea;
    private javax.swing.JScrollPane scrollPane;

    /** Constructor. Instatiates class.*/
    public imageNotes() {
        super("Image Notes:");
        super.setDefaultCloseOperation(PlugInJFrame.HIDE_ON_CLOSE);
        setSize(new java.awt.Dimension(350, 400));

        scrollPane = new javax.swing.JScrollPane();
        this.add(scrollPane);
        textArea = new javax.swing.JTextArea("");
        textArea.setColumns(50);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        scrollPane.setViewportView(textArea);

    }

    /**
     * Gets the text entered into the text area.
     * Returns a formatted String.
     *
     * @return
     */
    public String getOutputFormatedText() {
        String text = textArea.getText();
        text = text.replaceAll("(\r)|(\f)", "\n");
        return text.replaceAll("\n", "&/&/&");
    }

    /**
     * Sets the test in the text area. Must be
     * formatted by the <code>getOutputFormatedText</code> method.
     * @param text
     */
    public void setOutputFormatedText(String text) {
        textArea.setText(text.replaceAll("&/&/&","\n"));
    }
}