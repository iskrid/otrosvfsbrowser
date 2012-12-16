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

import pl.otros.vfs.browser.JOtrosVfsBrowserDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationTargetException;

/**
 */
public class TestDialog extends JFrame {

  private final JTextField textField;

  public static void main(String[] args) throws InvocationTargetException, InterruptedException {
    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
                      new TestDialog();
      }
    });
  }

  public TestDialog(){
    final JOtrosVfsBrowserDialog jOtrosVfsBrowserDialog = new JOtrosVfsBrowserDialog();
    Action a = new AbstractAction("Select file") {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {

        if (JOtrosVfsBrowserDialog.ReturnValue.Approve.equals(jOtrosVfsBrowserDialog.showOpenDialog(TestDialog.this,"title"))){
            textField.setText(jOtrosVfsBrowserDialog.getSelectedFile().getName().getFriendlyURI());
        }
      }
    };
    getContentPane().setLayout(new FlowLayout());
    getContentPane().add(new JButton(a));
    textField = new JTextField(60);
    getContentPane().add(textField);
    pack();
    setTitle("OtrosVfsBrowser demo");
    setVisible(true);
    setDefaultCloseOperation(EXIT_ON_CLOSE);

  }
}
