import {jahiaComponent} from "@jahia/javascript-modules-library";

interface RichTextProps {
    text?: string;
}

jahiaComponent(
    {
        componentType: "view",
        nodeType: "fmdb:richText",
        name: "default"
    },
    ({text}: RichTextProps) => {
        if (!text) {
            return null;
        }

        return <div className="fmdb-content fmdb-content-text"
										dangerouslySetInnerHTML={{ __html: text }} />;
    }
);

