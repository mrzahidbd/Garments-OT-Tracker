// Save reports with Android's document picker or the browser's download control.
function saveTrackerFile(name, mimeType, contents) {
    if (window.TrackerFiles && typeof window.TrackerFiles.postMessage === 'function') {
        window.TrackerFiles.postMessage(JSON.stringify({ name, contents }));
        return;
    }
    const blob = new Blob([contents], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}
