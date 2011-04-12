/* Copyright (c) 2010, skobbler GmbH
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the project nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.openstreetmap.josm.plugins.mapdust.gui.action.show;


import java.awt.event.ActionEvent;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;
import org.openstreetmap.josm.plugins.mapdust.MapdustPlugin;
import org.openstreetmap.josm.plugins.mapdust.gui.component.dialog.ChangeBugStatusDialog;
import org.openstreetmap.josm.plugins.mapdust.gui.component.panel.MapdustButtonPanel;


/**
 * Displays the <code>ChangeIssueStatusDialog</code> dialog window of type
 * 'reopen'. This action is executed whenever the user clicks on the
 * "Re-open bug report" button or selects the "Re-open bug" option from
 * the pop-up menu.
 *
 * @author Bea
 */
public class ShowReOpenBugAction extends MapdustShowAction {

    /** The serial version UID */
    private static final long serialVersionUID = -1362380763238161011L;

    /**
     * Builds a <code>ShowReOpenBugAction</code> object
     */
    public ShowReOpenBugAction() {}

    /**
     * Builds a <code>ShowReOpenBugAction</code> object based on the given
     * argument
     *
     * @param mapdustPlugin The <code>MapdustPlugin</code> action.
     */
    public ShowReOpenBugAction(MapdustPlugin mapdustPlugin) {
        setMapdustPlugin(mapdustPlugin);
        setTitle("Reopen bug report");
        setIconName("dialogs/reopen.png");
    }

    /**
     * Builds a <code>ChangeIssueStatusDialog</code> dialog window of type
     * "re-open" and displays on the screen.
     *
     * @param event The <code>ActionEvent</code> object
     */
    @Override
    public void actionPerformed(ActionEvent event) {
        if (event != null) {
            JToggleButton btn = null;
            if (event.getSource() instanceof JToggleButton) {
                btn = (JToggleButton) event.getSource();
                btn.setSelected(true);
            } if (event.getSource() instanceof JMenuItem){
                getButtonPanel().getBtnReOpenBugReport().setSelected(true);
            }
            disableButtons(getButtonPanel());
            ChangeBugStatusDialog dialog = new ChangeBugStatusDialog(
                    getTitle(), getIconName(), "reopen", btn,
                    getMapdustPlugin());
            dialog.setLocationRelativeTo(null);
            dialog.getContentPane().setPreferredSize(dialog.getSize());
            dialog.pack();
            dialog.setVisible(true);
        }
    }

    /**
     * Disables the buttons from the <code>MapdustButtonPanel</code> according
     * to the executed action type. The only enabled button will be the
     * "Re-open bug report" button.
     *
     * @param buttonPanel The <code>MapdustButtonPanel</code> object
     */
    @Override
    void disableButtons(MapdustButtonPanel buttonPanel) {
        if (buttonPanel != null) {
            buttonPanel.getBtnWorkOffline().setEnabled(false);
            buttonPanel.getBtnWorkOffline().setSelected(false);
            buttonPanel.getBtnWorkOffline().setFocusable(false);
            buttonPanel.getBtnRefresh().setEnabled(false);
            buttonPanel.getBtnRefresh().setSelected(false);
            buttonPanel.getBtnRefresh().setFocusable(false);
            buttonPanel.getBtnFilter().setEnabled(false);
            buttonPanel.getBtnFilter().setSelected(false);
            buttonPanel.getBtnFilter().setFocusable(false);
            buttonPanel.getBtnAddComment().setEnabled(false);
            buttonPanel.getBtnAddComment().setSelected(false);
            buttonPanel.getBtnAddComment().setFocusable(false);
            buttonPanel.getBtnInvalidateBugReport().setEnabled(false);
            buttonPanel.getBtnInvalidateBugReport().setSelected(false);
            buttonPanel.getBtnInvalidateBugReport().setFocusable(false);
            buttonPanel.getBtnFixBugReport().setEnabled(false);
            buttonPanel.getBtnFixBugReport().setSelected(false);
            buttonPanel.getBtnFixBugReport().setFocusable(false);
        }
    }

}
