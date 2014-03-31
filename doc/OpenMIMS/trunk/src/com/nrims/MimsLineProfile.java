package com.nrims;

import com.nrims.plot.MimsChartFactory;
import com.nrims.plot.MimsChartPanel;
import com.nrims.plot.MimsXYPlot;
import ij.IJ;
import java.awt.Color;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ResourceBundle;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.util.ResourceBundleWrapper;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.ExtensionFileFilter;

/**
 * The MimsLineProfile class creates a line plot 
 * for line ROIs. The y-axis represents pixel value
 * (of the current image) and x-axis represents 
 * length along the line.
 *
 * @author cpoczatek
 */
public class MimsLineProfile extends JFrame {

    private JFreeChart chart;
    private MimsChartPanel chartPanel;
    private int linewidth = 1;
    private UI ui;

    public MimsLineProfile(final UI ui) {
        super("Dynamic Line Profile");
        this.setDefaultCloseOperation(this.DISPOSE_ON_CLOSE);
        this.ui = ui;

        XYDataset dataset = createDataset();
        chart = createChart(dataset);

        chartPanel = new MimsChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(500, 270));
        JPopupMenu menu = chartPanel.getPopupMenu();
        JMenuItem menuItem = new javax.swing.JMenuItem("Display text");
        menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               displayProfileData();
            }
         });

        menu.add(menuItem, 2);
        setContentPane(chartPanel);

         // Add menu item for showing/hiding crosshairs.
         JMenuItem xhairs = new JMenuItem("Show/Hide Crosshairs");
         xhairs.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               MimsJFreeChart.showHideCrossHairs(chartPanel);
            }
         });
         chartPanel.getPopupMenu().addSeparator();
         chartPanel.getPopupMenu().add(xhairs);

         // Replace Save As... menu item.
         chartPanel.getPopupMenu().remove(3);
         JMenuItem saveas = new JMenuItem("Save as...");
         saveas.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               saveAs();
            }
         });
         chartPanel.getPopupMenu().add(saveas, 3);

         KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(new KeyEventDispatcher() {
                public boolean dispatchKeyEvent(KeyEvent e) {
                    if (e.getID() == KeyEvent.KEY_PRESSED && thisHasFocus()) {
                        chartPanel.keyPressed(e);
                   }
                    return false;
                }
            });

        this.pack();
        this.setVisible(true);
    }
   
     /**
     * Creates a sample dataset.
     * 
     * @return a sample dataset.
     */
    private XYDataset createDataset() {
        
        XYSeries series1 = new XYSeries("N");
        series1.add(1.0, 2.0);
        series1.add(2.0, 2.0);
        series1.add(3.0, 2.0);
        series1.add(4.0, 2.0);
        series1.add(5.0, 2.0);
        series1.add(6.0, 2.0);
        series1.add(7.0, 2.0);
        series1.add(8.0, 2.0);

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series1);
                
        return dataset;
        
    }
    
    /**
     * Creates a chart.
     * 
     * @param dataset  the data for the chart.
     * 
     * @return a chart.
     */
    private JFreeChart createChart(final XYDataset dataset) {
        
        // create the chart...
        final JFreeChart chart = MimsChartFactory.createMimsXYLineChart(
            "",      // chart title
            "L",                      // x axis label
            "P",                      // y axis label
            dataset,                  // data
            PlotOrientation.VERTICAL,
            true,                     // include legend
            true,                     // tooltips
            false                     // urls
        );

      // get a reference to the plot for further customisation...
      MimsXYPlot plot = (MimsXYPlot) chart.getPlot();

      // Set colors.
      plot.setBackgroundPaint(Color.lightGray);
      plot.setDomainGridlinePaint(Color.white);
      plot.setRangeGridlinePaint(Color.white);

      // Movable range and domain.
      plot.setDomainPannable(true);
      plot.setRangePannable(true);

      // Allow crosshairs to 'focus' in on a given point.
      plot.setDomainCrosshairVisible(true);
      plot.setRangeCrosshairVisible(true);

        // change the auto tick unit selection to integer units only...        
        plot.getRangeAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        plot.getDomainAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
                
        return chart;
        
    }

    /**
     * Updates the data displayed in the chart.
     *
     * @param newdata the data for the chart.
     * @param name name of ROI.
     * @param width the line width.
     */
    public void updateData(double[] newdata, String name, int width) {
        if (newdata == null) {
            return;
        }

        linewidth = width;
        XYSeries series = new XYSeries(name + " w: "+width);
        for(int i = 0; i< newdata.length; i++) {
            series.add(i, newdata[i]);
        }
        
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series);
        MimsXYPlot plot = (MimsXYPlot) chart.getPlot();
        plot.setDataset(dataset);

        chart.fireChartChanged();
    }

    /**
     * Creates a table displaying the data contained in the plot.
     */
    private void displayProfileData() {
        MimsXYPlot plot = (MimsXYPlot) chart.getPlot();
        XYDataset data = plot.getDataset();

        ij.measure.ResultsTable table = new ij.measure.ResultsTable();
        table.setHeading(1, "Position");
        table.setHeading(2, plot.getLegendItems().get(0).getLabel() + " : width " + linewidth);
        //table.incrementCounter();

        //end of table bug?
        for (int i = 0; i < data.getItemCount(0); i++) {
            table.incrementCounter();
            table.addValue(1, data.getXValue(0, i));
            table.addValue(2, data.getYValue(0, i));
            
        }

        table.show("");
    }

   private boolean thisHasFocus(){
      return this.hasFocus();
   }

   /**
    * Saveas the chart as a .png
    */
    public void saveAs(){
       MimsJFileChooser fileChooser = new MimsJFileChooser(ui);
       fileChooser.setSelectedFile(new File(ui.getLastFolder(), ui.getImageFilePrefix() + ".png"));
       ResourceBundle localizationResources = ResourceBundleWrapper.getBundle("org.jfree.chart.LocalizationBundle");
       ExtensionFileFilter filter = new ExtensionFileFilter(
               localizationResources.getString("PNG_Image_Files"), ".png");
       fileChooser.addChoosableFileFilter(filter);
       fileChooser.setFileFilter(filter);
       int option = fileChooser.showSaveDialog(chartPanel);
       if (option == MimsJFileChooser.APPROVE_OPTION) {
          String filename = fileChooser.getSelectedFile().getPath();
          if (!filename.endsWith(".png")) {
             filename = filename + ".png";
          }
          try {
             ChartUtilities.saveChartAsPNG(new File(filename), chartPanel.getChart(), getWidth(), getHeight());
          } catch (IOException ioe) {
             IJ.error("Unable to save file.\n\n" + ioe.toString());
          }
       }
    }

}
