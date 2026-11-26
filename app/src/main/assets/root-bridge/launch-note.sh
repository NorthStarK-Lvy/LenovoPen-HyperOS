#!/system/bin/sh
# Runs as root from pen-bridge. $1 is --trigger (single click) or --screen (double click).
MODDIR=${0%/*}
click=create_note; double=screen_recognition; package=; double_package=; consumers=; guard=1
if [ -r "$MODDIR/config" ]; then
    # Plain assignments only: values written by the app are never evaluated as shell.
    while IFS='=' read -r key value; do
        case "$key" in
            click) click=$value ;;
            double) double=$value ;;
            package) package=$value ;;
            double_package) double_package=$value ;;
            consumers) consumers=$value ;;
            guard) guard=$value ;;
        esac
    done < "$MODDIR/config"
fi
if [ "$1" = "--screen" ]; then action=$double; target=$double_package; else action=$click; target=$package; fi
[ "$action" = "none" ] && { echo "NO_ACTION action=none"; exit 0; }

# Apps that handle the pen button themselves keep it; the global action is skipped for them.
if [ "$guard" = "1" ] && [ -n "$consumers" ]; then
    top=$(dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity=' |
        sed -n 's/.* u[0-9]* \([A-Za-z0-9_.]*\)\/.*/\1/p')
    case ",$consumers," in
        *",$top,"*) [ -n "$top" ] && { echo "SKIPPED consumer=$top"; exit 0; } ;;
    esac
fi

case "$action" in
    video)
        # Only short-video apps in the foreground; anything else gets no key.
        top=$(dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity=' |
            sed -n 's/.* u[0-9]* \([A-Za-z0-9_.]*\)\/.*/\1/p')
        case "$top" in
            com.ss.android.ugc.aweme|com.smile.gifmaker|com.kwai.video) ;;
            *) echo "VIDEO_SKIPPED top=$top"; exit 0 ;;
        esac
        if [ "$1" = "--screen" ]; then exec /system/bin/input keyevent 87; fi
        exec /system/bin/input keyevent 85 ;;
    create_note)
        export CLASSPATH="$MODDIR/quicknote.jar"
        exec /system/bin/app_process /system/bin --nice-name=lenovo-pen-quicknote io.myrom.pen.QuickNote --trigger ;;
    screen_recognition)
        export CLASSPATH="$MODDIR/quicknote.jar"
        exec /system/bin/app_process /system/bin --nice-name=lenovo-pen-quicknote io.myrom.pen.QuickNote --screen ;;
    screenshot)
        exec /system/bin/input keyevent KEYCODE_SYSRQ ;;
    open_app)
        case "$target" in
            ""|*[!A-Za-z0-9._]*) echo "OPEN_APP_INVALID_PACKAGE"; exit 1 ;;
        esac
        exec /system/bin/monkey -p "$target" -c android.intent.category.LAUNCHER 1 ;;
    *) echo "NO_ACTION action=$action"; exit 0 ;;
esac
