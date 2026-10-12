# Emoji 17正式数据

锁定Unicode17/Emoji17，用于默认呈现、标准VS15/VS16与完整RGI识别；ICU76.1的一般Unicode算法数据仍为16，不能混称整体升级17。不得用可选缺文件时静默回退旧属性。

- `emoji-data.txt`：[固定上游](https://unicode.org/Public/17.0.0/ucd/emoji/emoji-data.txt)；SHA256 `2cb2bb9455cda83e8481541ecf5b6dfda66a3bb89efa3fa7c5297eccf607b72b`。
- `emoji-variation-sequences.txt`：[固定上游](https://unicode.org/Public/17.0.0/ucd/emoji/emoji-variation-sequences.txt)；SHA256 `bb3d09ef03f206012c7532dd52dc0a21c9efddba0135ea4cf0d9201b8b9bba7e`。
- `emoji-sequences.txt`：[固定上游](https://unicode.org/Public/17.0.0/emoji/emoji-sequences.txt)；SHA256 `12cc8267dc33cbd11ed32bcf6fc5dc2ad9c7a77bae1bdfba2f41b1b9b3ead8dd`。
- `emoji-zwj-sequences.txt`：[固定上游](https://unicode.org/Public/17.0.0/emoji/emoji-zwj-sequences.txt)；SHA256 `5b25441daed2322b068c5e70cda522946a4f0274df864445a1965a92e5fc5cad`。

随附Unicode License v3。上列SHA256为固定上游原始文件；随包emoji-data只移除两行格式注释的末尾空白以通过diff门禁，数据记录不变。随包emoji-data SHA256 `04de886a878a6220d327118b178b45ea23560e9350596b0757d964037f8356ea`；其余数据原字节不变。
