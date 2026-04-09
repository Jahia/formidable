import {jahiaComponent, Render} from "@jahia/javascript-modules-library";
interface StepProps {
"jcr:title"?: string;
}
jahiaComponent(
{
componentType: "view",
nodeType: "fmdb:step",
name: "default"
},
({"jcr:title": title}: StepProps, {currentNode}) => {
const elements = Array.from(currentNode.getNodes());
return (
<div data-fmdb-step className="fmdb-step">
{title && <h2 className="fmdb-step-title">{title}</h2>}
{elements.map((element) => (
<Render key={element.getIdentifier()} node={element}/>
))}
</div>
);
}
);
