export type ReferenceImage = {
    id: string;
    name: string;
    type: string;
    dataUrl: string;
    url?: string;
    storageKey?: string;
};

/** 生成出来的图片：本地直连与托管云端两条路径的共同形状。 */
export type GeneratedImageItem = {
    id: string;
    dataUrl: string;
    mimeType?: string;
    bytes?: number;
};
