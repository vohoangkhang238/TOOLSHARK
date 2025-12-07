package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.enginee.EngineCallBack;
import com.sojourners.chess.linker.*;
import com.sojourners.chess.lock.SingleLock;
import com.sojourners.chess.lock.WorkerTask;
import com.sojourners.chess.menu.BoardContextMenu;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.model.ManualRecord;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.OpenBookManager;
import com.sojourners.chess.util.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Callback;

import javax.imageio.ImageIO;
import java.awt.Desktop; 
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Controller implements EngineCallBack, LinkerCallBack {

    @FXML private Canvas canvas;
    @FXML private BorderPane borderPane;
    @FXML private Label infoShowLabel;
    @FXML private ToolBar statusToolBar;
    @FXML private Label timeShowLabel;
    @FXML private SplitPane splitPane;
    @FXML private SplitPane splitPane2;
    @FXML private ListView<ThinkData> listView;
    @FXML private ComboBox<String> engineComboBox;
    @FXML private ComboBox<String> linkComboBox;
    @FXML private ComboBox<String> hashComboBox;
    @FXML private ComboBox<String> threadComboBox;
    @FXML private RadioMenuItem menuOfLargeBoard;
    @FXML private RadioMenuItem menuOfBigBoard;
    @FXML private RadioMenuItem menuOfMiddleBoard;
    @FXML private RadioMenuItem menuOfSmallBoard;
    @FXML private RadioMenuItem menuOfAutoFitBoard;
    @FXML private RadioMenuItem menuOfDefaultBoard;
    @FXML private RadioMenuItem menuOfCustomBoard;
    @FXML private CheckMenuItem menuOfStepTip;
    @FXML private CheckMenuItem menuOfStepSound;
    @FXML private CheckMenuItem menuOfLinkBackMode;
    @FXML private CheckMenuItem menuOfLinkAnimation;
    @FXML private CheckMenuItem menuOfShowStatus;
    @FXML private CheckMenuItem menuOfShowNumber;
    @FXML private CheckMenuItem menuOfTopWindow;
    @FXML private Button analysisButton;
    @FXML private Button blackButton;
    @FXML private Button redButton;
    @FXML private Button reverseButton;
    @FXML private Button newButton;
    @FXML private Button copyButton;
    @FXML private Button pasteButton;
    @FXML private Button backButton;
    @FXML private BorderPane charPane;
    @FXML private Button immediateButton;
    @FXML private Button bookSwitchButton;
    @FXML private Button linkButton;
    @FXML private TableView<ManualRecord> recordTable;
    @FXML private TableView<BookData> bookTable;

    private Properties prop;
    private Engine engine;
    private ChessBoard board;
    private AbstractGraphLinker graphLinker;
    private String fenCode;
    private List<String> moveList;
    private int p;
    private SingleLock lock = new SingleLock();
    private XYChart.Series lineChartSeries;

    private SimpleObjectProperty<Boolean> robotRed = new SimpleObjectProperty<>(false);
    private SimpleObjectProperty<Boolean> robotBlack = new SimpleObjectProperty<>(false);
    private SimpleObjectProperty<Boolean> robotAnalysis = new SimpleObjectProperty<>(false);
    private SimpleObjectProperty<Boolean> isReverse = new SimpleObjectProperty<>(false);
    private SimpleObjectProperty<Boolean> linkMode = new SimpleObjectProperty<>(false);
    private SimpleObjectProperty<Boolean> useOpenBook = new SimpleObjectProperty<>(false);

    private boolean redGo;
    private volatile boolean isThinking;

    // =========================================================
    // 1. KHỞI TẠO (ĐÃ DỌN DẸP SẠCH SẼ)
    // =========================================================
    public void initialize() {
        prop = Properties.getInstance();
        
        // Tắt Book ngay từ đầu
        prop.setBookSwitch(false);
        useOpenBook.setValue(false);

        // Init ảo cho ListView
        listView.setCellFactory(param -> new ListCell<ThinkData>());

        setButtonTips();
        initChessBoard();
        
        // [CLEAN] Các hàm init rác -> Gọi hàm rỗng
        initRecordTable(); 
        initBookTable(); 
        initEngineView(); 
        initLineChart(); 
        
        // [CLEAN] KHÔNG NẠP ENGINE NỮA (Shark lo phần này)
        // loadEngine(prop.getEngineName()); 
        
        initGraphLinker();
        initButtonListener();
        initAutoFitBoardListener();
        initCanvasDragListener();
        
        // Fix lỗi chớp chuột
        fixMouseFlicker();

        // [CLEAN] Ẩn panel bên phải
        Platform.runLater(() -> { if (splitPane != null) splitPane.setDividerPositions(1.0); });
    }

    private void fixMouseFlicker() {
        this.canvas.setOnMouseMoved(event -> event.consume());
    }

    // Các hàm rỗng (Placeholder)
    private void initLineChart() { }
    private void initRecordTable() { } 
    private void initBookTable() { }
    private void initEngineView() { }

    // =========================================================
    // 2. XỬ LÝ CLICK TỪ PHẦN MỀM -> ĐIỀU KHIỂN WEB
    // =========================================================

    @FXML
    public void canvasClick(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            // Cho phép đi quân tự do (Shark/Người dùng click)
            String move = board.mouseClick((int) event.getX(), (int) event.getY(), true, true);

            if (move != null) {
                goCallBack(move);
                
                // [MỚI] Nếu đang Link (kể cả Quan Sát), gửi lệnh click ra Web
                if (linkMode.getValue()) {
                    ChessBoard.Step step = board.stepForBoard(move);
                    trickAutoClick(step);
                }
            }
            BoardContextMenu.getInstance().hide();
        } else if (event.getButton() == MouseButton.SECONDARY) {
            BoardContextMenu.getInstance().show(this.canvas, Side.RIGHT, event.getX() - this.canvas.widthProperty().doubleValue(), event.getY());
        }
    }

    private void trickAutoClick(ChessBoard.Step step) {
        if (step != null) {
            int x1 = step.getFirst().getX(), y1 = step.getFirst().getY();
            int x2 = step.getSecond().getX(), y2 = step.getSecond().getY();
            
            // [FIX] Thêm điều kiện isReverse để hỗ trợ click khi Quan sát (robotBlack = false)
            if (robotBlack.getValue() || isReverse.getValue()) {
                y1 = 9 - y1;
                y2 = 9 - y2;
                x1 = 8 - x1;
                x2 = 8 - x2;
            }
            graphLinker.autoClick(x1, y1, x2, y2);
        }
        this.isThinking = false;
    }

    // =========================================================
    // 3. LOGIC AUTO (GIỮ NGUYÊN TỪ CODE ỔN ĐỊNH CỦA BẠN)
    // =========================================================

    private void setLinkMode(String t1) {
        if (linkMode.getValue()) {
            if ("自动走棋".equals(t1)) {
                engineStop();
                if (isReverse.getValue()) {
                    blackButton.setDisable(false); robotBlack.setValue(true);
                    redButton.setDisable(true); robotRed.setValue(false);
                    analysisButton.setDisable(true); robotAnalysis.setValue(false);
                    if (!redGo) engineGo();
                } else {
                    redButton.setDisable(false); robotRed.setValue(true);
                    blackButton.setDisable(true); robotBlack.setValue(false);
                    analysisButton.setDisable(true); robotAnalysis.setValue(false);
                    if (redGo) engineGo();
                }
            } else {
                analysisButton.setDisable(false); robotAnalysis.setValue(true);
                blackButton.setDisable(true); robotBlack.setValue(false);
                redButton.setDisable(true); robotRed.setValue(false);
                immediateButton.setDisable(true);
                engineGo();
            }
        }
    }

    private void engineGo() {
        // Hàm này vẫn giữ để logic không bị gãy, nhưng engine null nên không làm gì
        if (engine == null) return;
        this.isThinking = true;
        // ... (Code cũ)
    }

    private void goCallBack(String move) {
        if (p == 0) moveList.clear();
        else if (p < moveList.size()) for (int i = moveList.size() - 1; i >= p; i--) moveList.remove(i);
        
        moveList.add(move);
        p++;
        redGo = !redGo;

        if ((redGo && robotRed.getValue()) || (!redGo && robotBlack.getValue()) || robotAnalysis.getValue()) {
            engineGo();
        }
    }

    @Override
    public void bestMove(String first, String second) {
        if ((redGo && robotRed.getValue()) || (!redGo && robotBlack.getValue())) {
            ChessBoard.Step s = board.stepForBoard(first);
            Platform.runLater(() -> {
                board.move(s.getFirst().getX(), s.getFirst().getY(), s.getSecond().getX(), s.getSecond().getY());
                board.setTip(second, null);
                goCallBack(first);
            });
            if (linkMode.getValue()) {
                trickAutoClick(s);
            }
        }
    }

    // =========================================================
    // 4. CÁC HÀM LINKER (GIỮ NGUYÊN)
    // =========================================================

    @Override
    public void linkerInitChessBoard(String fenCode, boolean isReverse) {
        Platform.runLater(() -> {
            newChessBoard(fenCode);
            if (isReverse) {
                reverseButtonClick(null);
            }
            setLinkMode(linkComboBox.getValue());
        });
    }

    @Override
    public void linkerMove(int x1, int y1, int x2, int y2) {
        Platform.runLater(() -> {
            String move = board.move(x1, y1, x2, y2);
            if (move != null) {
                boolean red = XiangqiUtils.isRed(board.getBoard()[y2][x2]);
                if (isWatchMode() && (!redGo && red || redGo && !red)) {
                    System.out.println(move + "," + red + ", " + redGo);
                    switchPlayer(false);
                } else {
                    goCallBack(move);
                }
            }
        });
    }

    // =========================================================
    // 5. BOILERPLATE (CÁC HÀM UI ÍT DÙNG - ĐÃ CLEAN)
    // =========================================================

    @Override public void thinkDetail(ThinkData td) { 
        // Vẫn giữ tính toán logic nhưng không in ra UI
        if ((redGo && robotRed.getValue()) || (!redGo && robotBlack.getValue()) || robotAnalysis.getValue()) {
             td.generate(redGo, isReverse.getValue(), board);
        }
    }
    @Override public void showBookResults(List<BookData> list) { }
    @FXML public void newButtonClick(ActionEvent event) { if (linkMode.getValue()) stopGraphLink(); newChessBoard(null); }
    @FXML void boardStyleSelected(ActionEvent event) { RadioMenuItem item = (RadioMenuItem) event.getTarget(); if (item.equals(menuOfDefaultBoard)) prop.setBoardStyle(ChessBoard.BoardStyle.DEFAULT); else prop.setBoardStyle(ChessBoard.BoardStyle.CUSTOM); board.setBoardStyle(prop.getBoardStyle(), this.canvas); }
    @FXML void boardSizeSelected(ActionEvent event) { RadioMenuItem item = (RadioMenuItem) event.getTarget(); if (item.equals(menuOfLargeBoard)) prop.setBoardSize(ChessBoard.BoardSize.LARGE_BOARD); else if (item.equals(menuOfBigBoard)) prop.setBoardSize(ChessBoard.BoardSize.BIG_BOARD); else if (item.equals(menuOfMiddleBoard)) prop.setBoardSize(ChessBoard.BoardSize.MIDDLE_BOARD); else if (item.equals(menuOfAutoFitBoard)) prop.setBoardSize(ChessBoard.BoardSize.AUTOFIT_BOARD); else prop.setBoardSize(ChessBoard.BoardSize.SMALL_BOARD); board.setBoardSize(prop.getBoardSize()); if (prop.getBoardSize() == ChessBoard.BoardSize.AUTOFIT_BOARD) board.autoFitSize(borderPane.getWidth(), borderPane.getHeight(), splitPane.getDividerPositions()[0], prop.isLinkShowInfo()); }
    @FXML void linkBackModeChecked(ActionEvent event) { CheckMenuItem item = (CheckMenuItem) event.getTarget(); if (linkMode.getValue()) stopGraphLink(); prop.setLinkBackMode(item.isSelected()); }
    @FXML void linkSettingClick(ActionEvent e) { App.openLinkSetting(); }
    @FXML public void reverseButtonClick(ActionEvent event) { isReverse.setValue(!isReverse.getValue()); board.reverse(isReverse.getValue()); }
    @FXML private void linkButtonClick(ActionEvent e) { linkMode.setValue(!linkMode.getValue()); if (linkMode.getValue()) graphLinker.start(); else stopGraphLink(); }
    
    private void newChessBoard(String fenCode) {
        robotRed.setValue(false); redButton.setDisable(false); robotBlack.setValue(false); blackButton.setDisable(false); robotAnalysis.setValue(false); immediateButton.setDisable(false); isReverse.setValue(false); engineStop(); 
        board = new ChessBoard(this.canvas, prop.getBoardSize(), prop.getBoardStyle(), false, false, prop.isShowNumber(), fenCode); // Tắt sound
        redGo = StringUtils.isEmpty(fenCode) ? true : fenCode.contains("w"); this.fenCode = board.fenCode(redGo); moveList = new ArrayList<>(); p = 0; listView.getItems().clear(); this.infoShowLabel.setText(""); System.gc();
    }
    
    private void initEngineView() { refreshEngineComboBox(); for (int i = 1; i <= Runtime.getRuntime().availableProcessors(); i++) threadComboBox.getItems().add(String.valueOf(i)); hashComboBox.getItems().addAll("16", "32", "64", "128", "256", "512", "1024", "2048", "4096"); threadComboBox.setValue(String.valueOf(prop.getThreadNum())); hashComboBox.setValue(String.valueOf(prop.getHashSize())); }
    private void initGraphLinker() { try { this.graphLinker = com.sun.jna.Platform.isWindows() ? new WindowsGraphLinker(this) : (com.sun.jna.Platform.isLinux() ? new LinuxGraphLinker(this) : new MacosGraphLinker(this)); } catch (Exception e) { e.printStackTrace(); } linkComboBox.getItems().addAll("自动走棋", "观战模式"); linkComboBox.setValue("自动走棋"); }
    private void refreshEngineComboBox() { engineComboBox.getItems().clear(); for (EngineConfig ec : prop.getEngineConfigList()) engineComboBox.getItems().add(ec.getName()); engineComboBox.setValue(prop.getEngineName()); }
    private void initButtonListener() { addListener(redButton, robotRed); addListener(blackButton, robotBlack); addListener(analysisButton, robotAnalysis); addListener(reverseButton, isReverse); addListener(linkButton, linkMode); addListener(bookSwitchButton, useOpenBook); threadComboBox.getSelectionModel().selectedItemProperty().addListener((o,s,t1)->{ if(Integer.parseInt(t1)!=prop.getThreadNum()) prop.setThreadNum(Integer.parseInt(t1)); }); hashComboBox.getSelectionModel().selectedItemProperty().addListener((o,s,t1)->{ if(Integer.parseInt(t1)!=prop.getHashSize()) prop.setHashSize(Integer.parseInt(t1)); }); engineComboBox.getSelectionModel().selectedItemProperty().addListener((o,s,t1)->{ if(StringUtils.isNotEmpty(t1)&&!t1.equals(prop.getEngineName())){ prop.setEngineName(t1); robotRed.setValue(false); robotBlack.setValue(false); robotAnalysis.setValue(false); if(linkMode.getValue()) stopGraphLink(); loadEngine(t1); } }); linkComboBox.getSelectionModel().selectedItemProperty().addListener((o,s,t1)->{ setLinkMode(t1); }); }
    private void addListener(Button button, ObjectProperty property) { property.addListener((ChangeListener<Boolean>) (observableValue, aBoolean, t1) -> { if (t1) { button.getStylesheets().add(this.getClass().getResource("/style/selected-button.css").toString()); } else { button.getStylesheets().remove(this.getClass().getResource("/style/selected-button.css").toString()); } }); }
    private void loadEngine(String name) { } // Không load
    private void importFromImgFile(File f) { }
    private void initCanvasDragListener() { }
    private void initAutoFitBoardListener() { borderPane.widthProperty().addListener((o, n, t1) -> board.autoFitSize(t1.doubleValue(), borderPane.getHeight(), splitPane.getDividerPositions()[0], prop.isLinkShowInfo())); borderPane.heightProperty().addListener((o, n, t1) -> board.autoFitSize(borderPane.getWidth(), t1.doubleValue(), splitPane.getDividerPositions()[0], prop.isLinkShowInfo())); splitPane.getDividers().get(0).positionProperty().addListener((o, n, t1) -> board.autoFitSize(borderPane.getWidth(), borderPane.getHeight(), t1.doubleValue(), prop.isLinkShowInfo())); }
    public void initStage() { borderPane.setPrefWidth(prop.getStageWidth()); borderPane.setPrefHeight(prop.getStageHeight()); splitPane.setDividerPosition(0, prop.getSplitPos()); splitPane2.setDividerPosition(0, prop.getSplitPos2()); menuOfTopWindow.setSelected(prop.isTopWindow()); App.topWindow(prop.isTopWindow()); }
    private void setButtonTips() { }
    private void initBoardContextMenu() { }
    private void stopGraphLink() { graphLinker.stop(); engineStop(); redButton.setDisable(false); robotRed.setValue(false); blackButton.setDisable(false); robotBlack.setValue(false); analysisButton.setDisable(false); robotAnalysis.setValue(false); linkMode.setValue(false); }
    private void switchPlayer(boolean f) { engineStop(); graphLinker.pause(); boolean tmpRed = robotRed.getValue(), tmpBlack = robotBlack.getValue(), tmpAnalysis = robotAnalysis.getValue(), tmpLink = linkMode.getValue(), tmpReverse = isReverse.getValue(); String fenCode = board.fenCode(f ? !redGo : redGo); newChessBoard(fenCode); isReverse.setValue(tmpReverse); board.reverse(tmpReverse); robotRed.setValue(tmpRed); robotBlack.setValue(tmpBlack); robotAnalysis.setValue(tmpAnalysis); linkMode.setValue(tmpLink); graphLinker.resume(); if (robotRed.getValue() && redGo || robotBlack.getValue() && !redGo || robotAnalysis.getValue()) engineGo(); }
    
    // Các hàm UI thừa khác
    @FXML void stepTipChecked(ActionEvent event) { }
    @FXML void showNumberClick(ActionEvent event) { }
    @FXML void topWindowClick(ActionEvent event) { }
    @FXML void linkAnimationChecked(ActionEvent event) { }
    @FXML void stepSoundClick(ActionEvent event) { }
    @FXML void showStatusBarClick(ActionEvent event) { }
    @FXML private void bookSwitchButtonClick(ActionEvent e) { }
    @FXML public void aboutClick(ActionEvent e) { }
    @FXML public void homeClick(ActionEvent e) { }
    @FXML public void backButtonClick(ActionEvent event) { }
    @FXML public void regretButtonClick(ActionEvent event) { }
    @FXML void forwardButtonClick(ActionEvent event) { }
    @FXML void finalButtonClick(ActionEvent event) { }
    @FXML void frontButtonClick(ActionEvent event) { }
    @FXML public void copyButtonClick(ActionEvent e) { }
    @FXML public void pasteButtonClick(ActionEvent e) { }
    @FXML public void editChessBoardClick(ActionEvent e) { } 
    @FXML public void importImageMenuClick(ActionEvent e) { }
    @FXML public void exportImageMenuClick(ActionEvent e) { }
    @FXML public void engineManageClick(ActionEvent e) { }
    @FXML void localBookManageButtonClick(ActionEvent e) { }
    @FXML void timeSettingButtonClick(ActionEvent e) { }
    @FXML void bookSettingButtonClick(ActionEvent e) { }
    @FXML public void bookTableClick(MouseEvent event) { }
    @FXML void recordTableClick(MouseEvent event) { }
    @FXML public void immediateButtonClick(ActionEvent event) { }
    @FXML public void blackButtonClick(ActionEvent event) { }
    @FXML public void redButtonClick(ActionEvent event) { }
    @Override public char[][] getEngineBoard() { return board.getBoard(); }
    @Override public boolean isThinking() { return this.isThinking; }
    @Override public boolean isWatchMode() { return "观战模式".equals(linkComboBox.getValue()); }
}