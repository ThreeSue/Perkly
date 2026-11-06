declare namespace Upload {
	interface Rule {
		name: string;
		type: string;
		color: string;
		exts: string[];
	}

	interface Item {
		url?: string;
		uid?: string;
		progress?: number;
		preload?: string;
		error?: string;
		isPlay?: boolean;
		[key: string]: any;
	}

	interface Options {
		// 素材空间内部自行登记时关闭，避免重复记录。
		registerMaterial?: boolean;
		prefixPath?: string;
		onProgress?(progress: number): void;
		[key: string]: any;
	}

	type Response = Promise<{
		key: string;
		url: string;
		fileId: string;
	}>;

	interface Request {
		host: string;
		preview?: string;
		data?: any;
	}
}
