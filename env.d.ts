/// <reference types="vite/client" />

interface ImportMetaEnv {
	readonly VITE_NAME: string;
	readonly VITE_TENCENT_MAP_KEY?: string;
	readonly VITE_TIMEOUT: number;
}

interface ImportMeta {
	readonly env: ImportMetaEnv;
}
