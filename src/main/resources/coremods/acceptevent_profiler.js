var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');

var PROFILER_CLASS = 'com/breakinblocks/whysoslow/profiler/StartupProfiler';
var EVENT_CLASS = 'net/minecraftforge/eventbus/api/Event';
var MOD_CONTAINER_CLASS = 'net/minecraftforge/fml/ModContainer';

function buildStartHook() {
    var list = new InsnList();
    list.add(new VarInsnNode(Opcodes.ALOAD, 0));
    list.add(new MethodInsnNode(
        Opcodes.INVOKEVIRTUAL,
        MOD_CONTAINER_CLASS,
        'getModId',
        '()Ljava/lang/String;',
        false
    ));
    list.add(new VarInsnNode(Opcodes.ALOAD, 1));
    list.add(new MethodInsnNode(
        Opcodes.INVOKESTATIC,
        PROFILER_CLASS,
        'onModEventStart',
        '(Ljava/lang/String;L' + EVENT_CLASS + ';)V',
        false
    ));
    return list;
}

function buildEndHook() {
    var list = new InsnList();
    list.add(new VarInsnNode(Opcodes.ALOAD, 0));
    list.add(new MethodInsnNode(
        Opcodes.INVOKEVIRTUAL,
        MOD_CONTAINER_CLASS,
        'getModId',
        '()Ljava/lang/String;',
        false
    ));
    list.add(new VarInsnNode(Opcodes.ALOAD, 1));
    list.add(new MethodInsnNode(
        Opcodes.INVOKESTATIC,
        PROFILER_CLASS,
        'onModEventEnd',
        '(Ljava/lang/String;L' + EVENT_CLASS + ';)V',
        false
    ));
    return list;
}

function initializeCoreMod() {
    return {
        'profiler_acceptevent': {
            'target': {
                'type': 'METHOD',
                'class': 'net.minecraftforge.fml.javafmlmod.FMLModContainer',
                'methodName': 'acceptEvent',
                'methodDesc': '(Lnet/minecraftforge/eventbus/api/Event;)V'
            },
            'transformer': function(method) {
                ASMAPI.log('INFO', '[WhySoSlow] Transforming FMLModContainer.acceptEvent');
                method.instructions.insertBefore(method.instructions.getFirst(), buildStartHook());

                var instructions = method.instructions.toArray();
                for (var i = 0; i < instructions.length; i++) {
                    if (instructions[i].getOpcode() === Opcodes.RETURN) {
                        method.instructions.insertBefore(instructions[i], buildEndHook());
                    }
                }

                ASMAPI.log('INFO', '[WhySoSlow] FMLModContainer.acceptEvent transformation complete');
                return method;
            }
        }
    };
}
