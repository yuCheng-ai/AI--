export const truncateName = (name: string, max = 5) =>
  Array.from(name).length > max ? `${Array.from(name).slice(0, max).join('')}…` : name;
const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});
export const formatTime = (value: string) =>
  Number.isNaN(Date.parse(value)) ? '—' : dateFormatter.format(new Date(value));
export const formatSize = (bytes: number) =>
  bytes < 1048576
    ? `${Math.max(1, Math.round(bytes / 1024))} KB`
    : `${(bytes / 1048576).toFixed(1)} MB`;
export const formatDuration = (seconds: number) =>
  `${Math.floor(seconds / 60)
    .toString()
    .padStart(2, '0')}:${Math.floor(seconds % 60)
    .toString()
    .padStart(2, '0')}`;
export function downloadFile(name: string, value: string, type = 'text/plain;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([value], { type }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = name.replace(/[\\/:*?"<>|]/g, '_');
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}
