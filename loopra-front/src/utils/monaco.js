import * as monaco from 'monaco-editor'
import EditorWorker from 'monaco-editor/editor/editor.worker?worker&inline'
import CssWorker from 'monaco-editor/language/css/css.worker?worker&inline'
import HtmlWorker from 'monaco-editor/language/html/html.worker?worker&inline'
import JsonWorker from 'monaco-editor/language/json/json.worker?worker&inline'
import TypeScriptWorker from 'monaco-editor/language/typescript/ts.worker?worker&inline'

globalThis.MonacoEnvironment = {
  getWorker(_workerId, label) {
    if (label === 'json') return new JsonWorker()
    if (label === 'css' || label === 'scss' || label === 'less') return new CssWorker()
    if (label === 'html' || label === 'handlebars' || label === 'razor') return new HtmlWorker()
    if (label === 'typescript' || label === 'javascript') return new TypeScriptWorker()
    return new EditorWorker()
  }
}

export default monaco
