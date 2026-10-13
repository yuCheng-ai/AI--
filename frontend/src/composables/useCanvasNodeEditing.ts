import type { CanvasData } from '../domain/canvas';

// One history entry per edited field; focusing or browsing never consumes redo.
export function useCanvasNodeEditing(
  data: () => CanvasData,
  readOnly: () => boolean | undefined,
  checkpoint: () => void,
  update: (patch: Partial<CanvasData>) => void,
) {
  let editing = false;
  function beginEdit() {
    editing = false;
  }
  function input(event: Event, field: 'title' | 'text' | 'prompt') {
    const value = (event.target as HTMLInputElement).value;
    if (readOnly() || value === data()[field]) return;
    if (!editing) {
      checkpoint();
      editing = true;
    }
    update({ [field]: value });
  }
  function parameter(event: Event, field: 'ratio' | 'duration') {
    const value = (event.target as HTMLSelectElement).value;
    if (readOnly() || value === data()[field]) return;
    checkpoint();
    update({ [field]: value });
  }
  return { beginEdit, input, parameter };
}
