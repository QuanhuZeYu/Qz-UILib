function initializeCoreMod() {
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    return {
        'uilib_client_shutdown': {
            'target': { 'type': 'METHOD', 'class': 'net.minecraft.client.Minecraft', 'methodName': 'close', 'methodDesc': '()V' },
            'transformer': function(method) {
                method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    'club/heiqi/uilib/host/forge116/Forge116Client', 'close', '()V', false));
                return method;
            }
        }
    };
}
