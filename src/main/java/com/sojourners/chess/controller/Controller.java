package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.linker.*;
import com.sojourners.chess.lock.SingleLock;
import com.sojourners.chess.lock.WorkerTask;
import com.sojourners.chess.menu.BoardContextMenu;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.ManualRecord;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.OpenBookManager;
import com.sojourners.chess.util.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
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

// [CLEAN] Bỏ implements EngineCallBack vì không dùng Engine nữa
public class Controller implements LinkerCallBack {

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
    private ChessBoard board;
    private AbstractGraphLinker graphLinker;
    private String fenCode;
    private List<String> moveList;
    private int p;
    private SingleLock lock = new SingleLock();
    private XYChart.Series lineChartSeries;

    // Chỉ giữ lại các biến trạng thái cần thiết cho UI/Linker
    private SimpleObjectProperty<Boolean> isReverse = new SimpleObjectProperty<>(false);
    private SimpleObjectProperty<Boolean> linkMode = new SimpleObjectProperty<>(false);
    private SimpleObjectProperty<Boolean> useOpenBook = new SimpleObjectProperty<>(false);

    private boolean redGo;
    private volatile boolean isThinking; // Giữ lại để Linker không bị lỗi, nhưng luôn là false

    // =================================================================================
    // 1. KHỞI TẠO (INITIALIZE) - SIÊU NHẸ
    // =================================================================================
    public void initialize() {
        prop = Properties.getInstance();
        
        // Tắt hết tính năng phụ trợ
        prop.setBookSwitch(false); 
        useOpenBook.setValue(false);

        // Init ảo
        listView.setCellFactory(param -> new ListCell<ThinkData>());

        setButtonTips();
        initChessBoard();
        
        // [CLEAN] Các hàm init rỗng
        initRecordTable(); 
        initBookTable(); 
        initEngineView(); 
        initLineChart(); 
        
        // [CLEAN] Không load Engine nữa
        // loadEngine(prop.getEngineName()); <-- XÓA
        
        initGraphLinker();
        initButtonListener();
        initAutoFitBoardListener();
        initCanvasDragListener();

        // Ẩn panel phải
        Platform.runLater(() -> { if (splitPane != null) splitPane.setDividerPositions(1.0); });
    }

    // Các hàm rỗng để giữ cấu trúc
    private void initLineChart() { }
    private void initRecordTable() { } 
    private void initBookTable() { }
    private void initEngineView() { }

    // =================================================================================
    // 2. XỬ LÝ CLICK TỪ SHARK (CẦU NỐI ĐI)
    // =================================================================================

    @FXML
    public void canvasClick(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            // [ZOMBIE MODE] Luôn cho phép click, không quan tâm luật lệ Engine
            String move = board.mouseClick((int) event.getX(), (int) event.getY(), true, true);

            if (move != null) {
                // Cập nhật bàn cờ TChess
                goCallBack(move);
                
                // Nếu đang Bật Kết Nối -> Gửi lệnh click sang Web ngay lập tức
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

    // Hàm chuyển đổi tọa độ và click sang Web
    private void trickAutoClick(ChessBoard.Step step) {
        if (step != null) {
            int x1 = step.getFirst().getX(), y1 = step.getFirst().getY();
            int x2 = step.getSecond().getX(), y2 = step.getSecond().getY();
            
            // [FIX] Nếu bàn cờ đang Lật (Reverse=true) -> Đảo tọa độ click
            // (Không cần check robotBlack nữa vì không dùng Engine)
            if (isReverse.getValue()) {
                y1 = 9 - y1; y2 = 9 - y2;
                x1 = 8 - x1; x2 = 8 - x2;
            }
            graphLinker.autoClick(x1, y1, x2, y2);
        }
    }

    // =================================================================================
    // 3. XỬ LÝ QUÉT TỪ WEB (CẦU NỐI VỀ)
    // =================================================================================

    @Override
    public void linkerInitChessBoard(String fenCode, boolean isReverseDetected) {
        Platform.runLater(() -> {
            newChessBoard(fenCode);
            // Tự động lật bàn theo nhận diện AI
            if (isReverseDetected != this.isReverse.getValue()) {
                reverseButtonClick(null);
            }
            // Xác định lượt đi
            String startFen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR";
            String currentFen = this.fenCode != null && this.fenCode.contains(" ") ? this.fenCode.split(" ")[0] : this.fenCode;
            
            if (currentFen != null && !currentFen.equals(startFen)) {
                redGo = false; // Bàn cờ đã biến đổi -> Đen đi
                this.fenCode = board.fenCode(redGo);
            }
        });
    }

    @Override
    public void linkerMove(int x1, int y1, int x2, int y2) {
        Platform.runLater(() -> {
            // Cập nhật nước đi từ Web vào TChess để Shark nhìn thấy
            String move = board.move(x1, y1, x2, y2);
            if (move != null) {
                goCallBack(move);
            }
        });
    }

    // =================================================================================
    // 4. CÁC HÀM HỖ TRỢ & GUI
    // =================================================================================

    private void goCallBack(String move) {
        if (p == 0) moveList.clear();
        else if (p < moveList.size()) for (int i = moveList.size() - 1; i >= p; i--) moveList.remove(i);
        moveList.add(move);
        p++;
        redGo = !redGo; 
        // [CLEAN] Không gọi Engine tính toán gì nữa
    }

    private void newChessBoard(String fenCode) {
        isReverse.setValue(false);
        // Tắt sound, tắt tip
        board = new ChessBoard(this.canvas, prop.getBoardSize(), prop.getBoardStyle(), false, false, prop.isShowNumber(), fenCode);
        redGo = StringUtils.isEmpty(fenCode) ? true : fenCode.contains("w");
        this.fenCode = board.fenCode(redGo);
        moveList = new ArrayList<>();
        p = 0;
        listView.getItems().clear();
        this.infoShowLabel.setText("");
        System.gc();
    }

    // Các hàm Linker
    private void initGraphLinker() { 
        try { 
            this.graphLinker = com.sun.jna.Platform.isWindows() ? new WindowsGraphLinker(this) : (com.sun.jna.Platform.isLinux() ? new LinuxGraphLinker(this) : new MacosGraphLinker(this)); 
        } catch (Exception e) { e.printStackTrace(); } 
        linkComboBox.getItems().addAll("Bắt cầu (Shark)", "Quan sát");
        linkComboBox.setValue("Bắt cầu (Shark)");
    }

    private void initButtonListener() { 
        // Chỉ giữ listener cho các nút quan trọng
        reverseButton.getStylesheets().remove("/style/selected-button.css"); // Reset style
        // Link Button
        linkButton.setOnAction(e -> {
            linkMode.setValue(!linkMode.getValue());
            if (linkMode.getValue()) {
                graphLinker.start();
                linkButton.getStylesheets().add(this.getClass().getResource("/style/selected-button.css").toString());
            } else {
                graphLinker.stop();
                linkButton.getStylesheets().remove(this.getClass().getResource("/style/selected-button.css").toString());
            }
        });
        
        // Reverse Button Listener
        isReverse.addListener((obs, old, newVal) -> {
            if (newVal) reverseButton.getStylesheets().add(this.getClass().getResource("/style/selected-button.css").toString());
            else reverseButton.getStylesheets().remove(this.getClass().getResource("/style/selected-button.css").toString());
        });
    }

    @Override public char[][] getEngineBoard() { return board.getBoard(); }
    @Override public boolean isThinking() { return false; } // Luôn rảnh rỗi để quét liên tục
    @Override public boolean isWatchMode() { return true; } // Luôn coi như WatchMode để Linker chỉ nhận diện, không tự quyết

    // --- CÁC HÀM UI THỪA (ĐỂ TRỐNG) ---
    @FXML public void newButtonClick(ActionEvent event) { if (linkMode.getValue()) graphLinker.stop(); newChessBoard(null); }
    @FXML void boardStyleSelected(ActionEvent event) { RadioMenuItem item = (RadioMenuItem) event.getTarget(); if (item.equals(menuOfDefaultBoard)) prop.setBoardStyle(ChessBoard.BoardStyle.DEFAULT); else prop.setBoardStyle(ChessBoard.BoardStyle.CUSTOM); board.setBoardStyle(prop.getBoardStyle(), this.canvas); }
    @FXML void boardSizeSelected(ActionEvent event) { RadioMenuItem item = (RadioMenuItem) event.getTarget(); if (item.equals(menuOfLargeBoard)) prop.setBoardSize(ChessBoard.BoardSize.LARGE_BOARD); else if (item.equals(menuOfBigBoard)) prop.setBoardSize(ChessBoard.BoardSize.BIG_BOARD); else if (item.equals(menuOfMiddleBoard)) prop.setBoardSize(ChessBoard.BoardSize.MIDDLE_BOARD); else if (item.equals(menuOfAutoFitBoard)) prop.setBoardSize(ChessBoard.BoardSize.AUTOFIT_BOARD); else prop.setBoardSize(ChessBoard.BoardSize.SMALL_BOARD); board.setBoardSize(prop.getBoardSize()); if (prop.getBoardSize() == ChessBoard.BoardSize.AUTOFIT_BOARD) board.autoFitSize(borderPane.getWidth(), borderPane.getHeight(), splitPane.getDividerPositions()[0], prop.isLinkShowInfo()); }
    @FXML void linkBackModeChecked(ActionEvent event) { CheckMenuItem item = (CheckMenuItem) event.getTarget(); if (linkMode.getValue()) graphLinker.stop(); prop.setLinkBackMode(item.isSelected()); }
    @FXML void linkSettingClick(ActionEvent e) { App.openLinkSetting(); }
    @FXML public void reverseButtonClick(ActionEvent event) { isReverse.setValue(!isReverse.getValue()); board.reverse(isReverse.getValue()); }
    @FXML public void copyButtonClick(ActionEvent e) { String fenCode = board.fenCode(redGo); ClipboardUtils.setText(fenCode); }
    @FXML public void pasteButtonClick(ActionEvent e) { String fenCode = ClipboardUtils.getText(); if (StringUtils.isNotEmpty(fenCode) && fenCode.split("/").length == 10) newFromOriginFen(fenCode); }
    private void newFromOriginFen(String fenCode) { if (StringUtils.isNotEmpty(fenCode)) { if (linkMode.getValue()) graphLinker.stop(); newChessBoard(fenCode); if (XiangqiUtils.isReverse(fenCode)) reverseButtonClick(null); } }
    public void initStage() { borderPane.setPrefWidth(prop.getStageWidth()); borderPane.setPrefHeight(prop.getStageHeight()); splitPane.setDividerPosition(0, prop.getSplitPos()); splitPane2.setDividerPosition(0, prop.getSplitPos2()); menuOfTopWindow.setSelected(prop.isTopWindow()); App.topWindow(prop.isTopWindow()); }
    private void setButtonTips() { newButton.setTooltip(new Tooltip("新局面")); copyButton.setTooltip(new Tooltip("复制局面")); pasteButton.setTooltip(new Tooltip("粘贴局面")); backButton.setTooltip(new Tooltip("悔棋")); reverseButton.setTooltip(new Tooltip("翻转")); linkButton.setTooltip(new Tooltip("连线")); }
    private void initBoardContextMenu() { BoardContextMenu.getInstance().setOnAction(e -> { MenuItem item = (MenuItem) e.getTarget(); if ("复制局面FEN".equals(item.getText())) copyButtonClick(null); else if ("粘贴局面FEN".equals(item.getText())) pasteButtonClick(null); }); }
    
    // Các hàm Dead code (Không dùng nữa)
    @FXML void stepTipChecked(ActionEvent event) { }
    @FXML void showNumberClick(ActionEvent event) { }
    @FXML void topWindowClick(ActionEvent event) { }
    @FXML void linkAnimationChecked(ActionEvent event) { }
    @FXML void stepSoundClick(ActionEvent event) { }
    @FXML void showStatusBarClick(ActionEvent event) { }
    @FXML public void analysisButtonClick(ActionEvent event) { }
    @FXML public void immediateButtonClick(ActionEvent event) { }
    @FXML public void blackButtonClick(ActionEvent event) { }
    @FXML public void engineManageClick(ActionEvent e) { }
    @FXML public void redButtonClick(ActionEvent event) { }
    @FXML void recordTableClick(MouseEvent event) { } 
    @FXML public void backButtonClick(ActionEvent event) { }
    @FXML public void regretButtonClick(ActionEvent event) { }
    @FXML void forwardButtonClick(ActionEvent event) { }
    @FXML void finalButtonClick(ActionEvent event) { }
    @FXML void frontButtonClick(ActionEvent event) { }
    @FXML public void importImageMenuClick(ActionEvent e) { }
    @FXML public void exportImageMenuClick(ActionEvent e) { }
    @FXML public void aboutClick(ActionEvent e) { }
    @FXML public void homeClick(ActionEvent e) { }
    @FXML void localBookManageButtonClick(ActionEvent e) { }
    @FXML void timeSettingButtonClick(ActionEvent e) { }
    @FXML void bookSettingButtonClick(ActionEvent e) { }
    @FXML private void bookSwitchButtonClick(ActionEvent e) { }
    @FXML private void linkButtonClick(ActionEvent e) { }
    @FXML public void bookTableClick(MouseEvent event) { }
    @FXML public void editChessBoardClick(ActionEvent e) { }
    @FXML public void exit() { OpenBookManager.getInstance().close(); graphLinker.stop(); prop.save(); Platform.exit(); }
    
    // Các phương thức Interface còn lại của EngineCallBack (để trống)
    // Lưu ý: Cần xóa "implements EngineCallBack" ở đầu class để sạch hoàn toàn, 
    // nhưng nếu lười sửa file khác thì để trống method cũng được.
    @Override public void bestMove(String f, String s) {} 
}