/*
 * Copyright 2012 Krzysztof Otrebski (krzysztof.otrebski@gmail.com)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package pl.otros.vfs.browser.demo;

import com.google.common.base.Throwables;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DataConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.vfs2.FileObject;
import pl.otros.vfs.browser.SelectionMode;
import pl.otros.vfs.browser.VfsBrowser;
import pl.otros.vfs.browser.table.FileSize;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.lang.reflect.InvocationTargetException;

public class TestBrowser {

  public static void main(final String[] args) throws InterruptedException, InvocationTargetException, SecurityException, IOException {
      if (args.length > 1)
          throw new IllegalArgumentException("SYNTAX:  java... "
                  + TestBrowser.class.getName() + " [initialPath]");

    SwingUtilities.invokeAndWait(new Runnable() {

      @Override
      public void run() {

        final JFrame f = new JFrame("OtrosVfsBrowser demo");
        Container contentPane = f.getContentPane();
        contentPane.setLayout(new BorderLayout());
        DataConfiguration dc = null;
        final PropertiesConfiguration propertiesConfiguration = new PropertiesConfiguration();
        File favoritesFile = new File("favorites.properties");
        propertiesConfiguration.setFile(favoritesFile);
        if (favoritesFile.exists()) {
          try {
            propertiesConfiguration.load();
          } catch (ConfigurationException e) {
            e.printStackTrace();
          }
        }
        dc = new DataConfiguration(propertiesConfiguration);
        propertiesConfiguration.setAutoSave(true);
        final VfsBrowser comp =
                new VfsBrowser(dc, (args.length > 0) ? args[0] : null);
        comp.setSelectionMode(SelectionMode.FILES_ONLY);
        comp.setMultiSelectionEnabled(true);
        comp.setApproveAction(new AbstractAction("Show content") {
          @Override
          public void actionPerformed(ActionEvent e) {
            FileObject[] selectedFiles = comp.getSelectedFiles();
            System.out.println("Selected files count=" + selectedFiles.length);
            for (FileObject selectedFile : selectedFiles) {
              try {
                FileSize fileSize = new FileSize(selectedFile.getContent().getSize());
                System.out.println(selectedFile.getName().getURI() + ": " + fileSize.toString());
                byte[] bytes = readBytes(selectedFile.getContent().getInputStream(), 150 * 1024l);
                JScrollPane sp = new JScrollPane(new JTextArea(new String(bytes)));
                JDialog d = new JDialog(f);
                d.setTitle("Content of file: " + selectedFile.getName().getFriendlyURI());
                d.getContentPane().add(sp);
                d.setSize(600, 400);
                d.setVisible(true);
              } catch (Exception e1) {
                e1.printStackTrace();
                JOptionPane.showMessageDialog(f, "Error:\n" + Throwables.getStackTraceAsString(e1), "Error", JOptionPane.ERROR_MESSAGE);
              }
            }
          }
        });

        comp.setCancelAction(new AbstractAction("Cancel") {
          @Override
          public void actionPerformed(ActionEvent e) {
            f.dispose();
            try {
              propertiesConfiguration.save();
            } catch (ConfigurationException e1) {
              e1.printStackTrace();
            }
            System.exit(0);
          }
        });
        contentPane.add(comp);

        f.pack();
        f.setVisible(true);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

      }
    });
  }

  private static byte[] readBytes(InputStream inputStream, long max) throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream((int) max);
    byte[] buff = new byte[1024];
    BufferedInputStream bin = new BufferedInputStream(inputStream);
    int read = 0;
    while ((read = bin.read(buff)) > 0 && bout.size() < max) {
      bout.write(buff, 0, read);
    }


    return bout.toByteArray();  //To change body of created methods use File | Settings | File Templates.
  }
}
