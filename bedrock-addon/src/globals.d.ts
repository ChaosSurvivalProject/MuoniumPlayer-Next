/**
 * Script API 运行时提供的全局对象。
 * 不引入 DOM lib（那是浏览器类型，会误导），只声明本项目实际使用的 console 方法。
 */
declare const console: {
    warn(...data: readonly unknown[]): void;
    log(...data: readonly unknown[]): void;
    error(...data: readonly unknown[]): void;
};
