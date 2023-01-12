# ZipLine
`/zipline stick`でルート作成用の棒を取得してルートを設定します。<br>
設定が完了したら`/zipline create [name]`でジップラインを作成できます。[name]は任意の値です。<br>
あとは設定したルートに近づくと自動的にジップラインに乗せられます。
# 技術的な話
ジップラインを作成する上でどうしてもnmsを使わざるを得なかったため[EntityUtil.kt](src%2Fmain%2Fkotlin%2Fdev%2Fmr3n%2Fzipline%2Fnms%2FEntityUtil.kt)でnmsを使用しています。<br>
もし何かしらサーバーとの互換性が失われている場合はそのクラスを変更してください。<br>
基本的にこのクラスとの互換性がなくなるとジップラインの椅子がスポーン後に移動しなくなります。
