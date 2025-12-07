package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.DialogUtils;
import com.sojourners.chess.util.StringUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

public class LinkSettingController {

    @FXML
    private TextField linkScanTime;
    @FXML
    private TextField linkThreadNum;

    @FXML
    private TextField mouseClickDelay;

    @FXML
    private TextField mouseMoveDelay;

    private Properties prop;

    @FXML
    void cancelButtonClick(ActionEvent e) {
        App.closeLinkSetting();
    }

    @FXML
    void okButtonClick(ActionEvent e) {

        String txt = linkScanTime.getText();
        if (!StringUtils.isPositiveInt(txt)) {
            DialogUtils.showErrorDialog("Lỗi", "Thời gian quét phải là số dương!");
            return;
        }
        prop.setLinkScanTime(Long.parseLong(txt));
        
        txt = linkThreadNum.getText();
        if (!StringUtils.isPositiveInt(txt)) {
            DialogUtils.showErrorDialog("Lỗi", "Số luồng quét phải là số dương!");
            return;
        }
        prop.setLinkThreadNum(Integer.parseInt(txt));

        txt = mouseClickDelay.getText();
        if (!StringUtils.isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("Lỗi", "Độ trễ click chuột không hợp lệ!");
            return;
        }
        prop.setMouseClickDelay(Integer.parseInt(txt));
        
        txt = mouseMoveDelay.getText();
        if (!StringUtils.isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("Lỗi", "Độ trễ di chuyển chuột không hợp lệ!");
            return;
        }
        prop.setMouseMoveDelay(Integer.parseInt(txt));

        // Lưu cấu hình xuống file
        prop.save(); 

        App.closeLinkSetting();
    }

    public void initialize() {
        prop = Properties.getInstance();

        if(prop != null) {
            linkScanTime.setText(String.valueOf(prop.getLinkScanTime()));
            linkThreadNum.setText(String.valueOf(prop.getLinkThreadNum()));
            mouseClickDelay.setText(String.valueOf(prop.getMouseClickDelay()));
            mouseMoveDelay.setText(String.valueOf(prop.getMouseMoveDelay()));
        }
    }
}