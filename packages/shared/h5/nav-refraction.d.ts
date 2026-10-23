export type NavRefraction = {
  update(
    progress: number,
    stretch: number,
    squash: number,
    expansion?: number,
    tilt?: number,
  ): void;
  destroy(): void;
};
export function mountNavRefraction(
  host: HTMLElement,
  ready: (enabled: boolean) => void,
): NavRefraction | null;
