export type NavRefraction = {
  update(
    progress: number,
    stretch: number,
    squash: number,
    expansion?: number,
    tilt?: number,
    speed?: number,
  ): void;
  destroy(): void;
};
export function supportsNavRefraction(signals?: {
  userAgent?: string;
  brands?: { brand: string }[];
  chromiumRuntime?: boolean;
  backdropFilter?: boolean;
}): boolean;
export function mountNavRefraction(
  host: HTMLElement,
  ready: (enabled: boolean) => void,
  options?: { chromaticSpread?: number; speedResponsive?: boolean },
): NavRefraction | null;
