function doGet() {
  const t = HtmlService.createTemplateFromFile('index');
  return t.evaluate()
    .setTitle('JAN 48面（シート連携・印刷プレビュー）')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}
// ▼ 保存先フォルダIDの保存・取得
function setSaveFolderId(id){
  try{
    DriveApp.getFolderById(id).getId(); // 存在チェック（権限含む）
  }catch(e){
    throw new Error('フォルダIDが不正です（アクセス権またはIDを確認）');
  }
  PropertiesService.getUserProperties().setProperty('SAVE_FOLDER_ID', id);
  return true;
}

function getSaveFolderId(){
  return PropertiesService.getUserProperties().getProperty('SAVE_FOLDER_ID') || '';
}

// ▼ PDF保存本体（Base64→Blob→Drive）
function savePdfToDrive(b64, filename){
  const folderId = getSaveFolderId();
  if (!folderId) throw new Error('保存先フォルダが未設定です。「保存フォルダ設定」で登録してください。');
  const bytes = Utilities.base64Decode(b64);
  const blob  = Utilities.newBlob(bytes, 'application/pdf', filename);
  const file  = DriveApp.getFolderById(folderId).createFile(blob);
  return file.getUrl(); // 保存URLを返す
}

// ▼ 日本語フォント（TTF/OTF）の Drive ファイルIDを保存
function setFontFileId(id){
  try { DriveApp.getFileById(id).getName(); }      // 存在/権限チェック
  catch(e){ throw new Error('フォントのファイルIDが不正です'); }
  PropertiesService.getUserProperties().setProperty('JP_FONT_FILE_ID', id);
  return true;
}

// ▼ Base64 をクライアントへ渡す（PDF埋め込み用）
function getFontBase64(){
  const id = PropertiesService.getUserProperties().getProperty('JP_FONT_FILE_ID');
  if (!id) throw new Error('フォント未設定です。「フォント設定」で登録してください');
  const blob  = DriveApp.getFileById(id).getBlob();
  return Utilities.base64Encode(blob.getBytes());
}


/** シートの KEY7→商品名 の辞書を返す（列名：コード / 商品名, シート名：Sheet1） */
function fetchDict() {
  const sh = SpreadsheetApp.getActive().getSheetByName('Sheet1');
  if (!sh) throw new Error('Sheet1 が見つかりません');
  const rng = sh.getDataRange().getDisplayValues(); // 文字列で取得（先頭ゼロ保持）
  const head = rng[0], rows = rng.slice(1);
  const idxCode = head.indexOf('コード');
  const idxName = head.indexOf('商品名');
  if (idxCode < 0 || idxName < 0) throw new Error('列名「コード」「商品名」を確認してください');

  const map = {};
  rows.forEach(r => {
    const k = (r[idxCode] || '').trim();  // 先頭7桁キー
    const v = (r[idxName] || '').trim();
    if (!k || !v) return;
    if (map[k]) throw new Error('「コード」が重複しています: ' + k);
    map[k] = v;
  });
  return map;
}
